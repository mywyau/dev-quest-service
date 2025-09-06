// services/stripe/StripeBilling.scala

package services.stripe

import cats.effect.Async
import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.syntax.all.*
import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.Subscription
import com.stripe.model.SubscriptionItem
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.net.Webhook
import com.stripe.param.SubscriptionUpdateParams
import com.stripe.param.checkout.SessionCreateParams
import configuration.AppConfig
import io.github.cdimascio.dotenv.Dotenv
import models.pricing.PlanSnapshot
import models.pricing.UserPricingPlanView
import org.typelevel.log4cats.Logger

import java.time.Instant
import scala.jdk.CollectionConverters.*

/** Minimal event payload your service needs */
final case class StripeEvent(
  eventType: String,
  userIdHint: Option[String],
  customerId: Option[String],
  subscriptionId: Option[String],
  priceId: Option[String],
  status: Option[String],
  currentPeriodEnd: Option[Instant],
  cancelAtPeriodEnd: Option[Boolean]
)

final case class StripeSubState(
  status: String, // e.g., "active"
  currentPeriodEnd: Option[Instant], // end of current cycle
  cancelAtPeriodEnd: Boolean
)

trait StripeBillingServiceAlgebra[F[_]] {

  /** Start a subscription checkout; returns hosted session URL */
  def createCheckoutSession(
    userId: String,
    priceId: String,
    successUrl: String,
    cancelUrl: String,
    idempotencyKey: String
  ): F[String]

  def createBillingPortalSession(customerId: String, returnUrl: String): F[String]

  /** Verify Stripe signature and parse into a compact event we can handle */
  def verifyAndParseEvent(rawPayload: String, signatureHeader: String): F[StripeEvent]

  def setCancelAtPeriodEnd(subscriptionId: String, cancel: Boolean): F[StripeSubState]
}

final class StripeBillingServiceImpl[F[_] : Async : Logger](appConfig: AppConfig) extends StripeBillingServiceAlgebra[F] {

  val secretKey: String =
    if (appConfig.featureSwitches.localTesting) {
      sys.env.getOrElse("STRIPE_TEST_PLAN_SECRET_KEY", Dotenv.load().get("STRIPE_TEST_PLAN_SECRET_KEY"))
    } else {
      sys.env.getOrElse("STRIPE_TEST_PLAN_SECRET_KEY", "")
    }

  val webhookSecret: String =
    if (appConfig.featureSwitches.localTesting) {
      sys.env.getOrElse("STRIPE_TEST_WEBHOOK_SECRET", Dotenv.load().get("STRIPE_TEST_WEBHOOK_SECRET"))
    } else {
      sys.env.getOrElse("STRIPE_TEST_WEBHOOK_SECRET", "")
    }

  private def toSnapshot(view: UserPricingPlanView, userId: String): PlanSnapshot =
    PlanSnapshot(
      userId = userId,
      planId = view.planRow.planId,
      status = view.userPlanRow.status,
      features = view.planRow.features,
      currentPeriodEnd = view.userPlanRow.currentPeriodEnd,
      cancelAtPeriodEnd = view.userPlanRow.cancelAtPeriodEnd
    )

  /** Ensure API key is set before each call (safe to set repeatedly) */
  private def init: F[Unit] =
    Async[F].delay(Stripe.apiKey = secretKey)

  private def parseEvent(event: Event): F[StripeEvent] = {

    val tpe = Option(event.getType).getOrElse("")
    val deser = event.getDataObjectDeserializer

    // Extract object if SDK can deserialize it
    val objOpt = if (deser.getObject.isPresent) Some(deser.getObject.get()) else None

    tpe match {
      // Checkout completed: fetch subscription to get price/status/periods
      case "checkout.session.completed" =>
        objOpt match {
          case Some(sess: Session) =>
            val userIdHint =
              Option(sess.getClientReferenceId)
                .orElse(Option(sess.getMetadata).flatMap(m => Option(m.get("user_id"))))
            val customerId = Option(sess.getCustomer)
            val subIdOpt = Option(sess.getSubscription)

            for {
              details <- fetchSubscriptionDetails(subIdOpt)
              (priceId, status, currentEnd, cancelAtPeriodEnd) = details
            } yield StripeEvent(
              eventType = tpe,
              userIdHint = userIdHint,
              customerId = customerId,
              subscriptionId = subIdOpt,
              priceId = priceId,
              status = status,
              currentPeriodEnd = currentEnd,
              cancelAtPeriodEnd = cancelAtPeriodEnd
            )

          case _ =>
            // Fallback: no session object; we can still return a minimal event
            Async[F].pure(StripeEvent(tpe, None, None, None, None, None, None, None))
        }

      // Subscription lifecycle events carry a Subscription object
      case "customer.subscription.created" | "customer.subscription.updated" | "customer.subscription.deleted" =>
        objOpt match {
          case Some(sub: Subscription) =>
            val (priceId, status, currentEnd, cancelAtPeriodEnd) = extractFromSubscription(sub)
            val userIdHint = Option(sub.getMetadata).flatMap(m => Option(m.get("user_id")))
            val customerId = Option(sub.getCustomer)
            val subId = Option(sub.getId)
            Async[F].pure(StripeEvent(tpe, userIdHint, customerId, subId, priceId, status, currentEnd, cancelAtPeriodEnd))
          case _ =>
            Async[F].pure(StripeEvent(tpe, None, None, None, None, None, None, None))
        }

      // Other events you may care about
      case "invoice.payment_failed" | "invoice.paid" =>
        Async[F].pure(StripeEvent(tpe, None, None, None, None, None, None, None))

      case _ =>
        // Unknown/unhandled—still return something useful for logging
        Async[F].pure(StripeEvent(tpe, None, None, None, None, None, None, None))
    }
  }

  override def createCheckoutSession(
    userId: String,
    priceId: String,
    successUrl: String,
    cancelUrl: String,
    idempotencyKey: String
  ): F[String] =
    for {
      _ <- init
      params <- Async[F].delay {
        SessionCreateParams
          .builder()
          .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
          .setSuccessUrl(s"$successUrl&session_id={CHECKOUT_SESSION_ID}") // keep session id for post-success fetch if needed
          .setCancelUrl(cancelUrl)
          .setClientReferenceId(userId) // helpful mapping
          .putMetadata("user_id", userId) // appears on session
          // Ensure we always get/create a customer
          // .setCustomerCreation(SessionCreateParams.CustomerCreation.ALWAYS)
          // Add the subscription line item
          .addLineItem(
            SessionCreateParams.LineItem
              .builder()
              .setPrice(priceId)
              .setQuantity(1L)
              .build()
          )
          // Also add metadata to the resulting subscription
          .setSubscriptionData(
            SessionCreateParams.SubscriptionData
              .builder()
              .putMetadata("user_id", userId)
              .build()
          )
          .build()
      }
      session <- Async[F].blocking {
        val opts = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
        Session.create(params, opts)
      }
      _ <- Logger[F].info(s"[Stripe] Created checkout session ${session.getId} for user=$userId price=$priceId")
    } yield session.getUrl

  override def verifyAndParseEvent(rawPayload: String, signatureHeader: String): F[StripeEvent] =
    for {
      _ <- init
      event <- Async[F].blocking(Webhook.constructEvent(rawPayload, signatureHeader, webhookSecret))
      _ <- Logger[F].debug(s"[Stripe] Webhook event type=${event.getType}")
      se <- parseEvent(event)
    } yield se

  // -------- helpers --------
  override def createBillingPortalSession(
    customerId: String,
    returnUrl: String
  ): F[String] = Sync[F].delay {

    val params = SessionCreateParams
      .builder()
      .setCustomer(customerId)
      .setReturnUrl(returnUrl)
      .build()

    val portalSession = Session.create(params)
    portalSession.getUrl
  }

//   /**
//    * If we only have a subscription ID, retrieve it to get the price and timing
//    */
//   private def fetchSubscriptionDetails(
//     subIdOpt: Option[String]
//   ): F[(Option[String], Option[String], Option[Instant], Option[Boolean])] =
//     subIdOpt match {
//       case None => Async[F].pure((None, None, None, None))
//       case Some(subId) =>
//         Async[F].blocking(Subscription.retrieve(subId)).map { sub =>
//           extractFromSubscription(sub)
//         }
//     }

//   private def extractFromSubscription(
//     sub: Subscription
//   ): (Option[String], Option[String], Option[Instant], Option[Boolean]) = {
//     val priceId =
//       Option(sub.getItems).flatMap(items => Option(items.getData)).flatMap { list =>
//         Option(list).flatMap { l =>
//           if (l.isEmpty) None
//           else Option(l.get(0)).flatMap(li => Option(li.getPrice).map(_.getId))
//         }
//       }

//     val status = Option(sub.getStatus)
//     val currentEnd = Option(sub.getCurrentPeriodEnd()).map(sec => Instant.ofEpochSecond(sec.toLong))
//     val cancelAtPeriodEnd = Option(sub.getCancelAtPeriodEnd).map(_.booleanValue()).map(_.booleanValue())

//     (priceId, status, currentEnd, cancelAtPeriodEnd)
//   }

  /**
   * Extracts (firstItemPriceId, status, maxCurrentPeriodEnd, cancelAtPeriodEnd)
   * from a v29 Subscription
   */

  private def extractFromSubscription(
    sub: Subscription
  ): (Option[String], Option[String], Option[Instant], Option[Boolean]) = {
    // Items list
    val items: List[SubscriptionItem] =
      Option(sub.getItems)
        .flatMap(c => Option(c.getData))
        .map(_.asScala.toList)
        .getOrElse(Nil)

    // Price id from the *first* item (adapt if you need a specific item)
    val priceId: Option[String] =
      items.headOption.flatMap(it => Option(it.getPrice).map(_.getId))

    // Status lives on the subscription
    val status: Option[String] = Option(sub.getStatus)

    // current_period_end moved to the *item*
    val maxCurrentPeriodEnd: Option[Instant] =
      items
        .flatMap(it => Option(it.getCurrentPeriodEnd)) // java.lang.Long (epoch seconds)
        .map(_.longValue())
        .map(Instant.ofEpochSecond)
        .sortBy(_.getEpochSecond)
        .lastOption

    // cancel_at_period_end still on the subscription
    val cancelAtPeriodEnd: Option[Boolean] =
      Option(sub.getCancelAtPeriodEnd).map(_.booleanValue())

    (priceId, status, maxCurrentPeriodEnd, cancelAtPeriodEnd)
  }

  /** If you only have the subscription id, fetch and then extract */
  private def fetchSubscriptionDetails(
    subIdOpt: Option[String]
  ): F[(Option[String], Option[String], Option[Instant], Option[Boolean])] =
    subIdOpt match {
      case None => Async[F].pure((None, None, None, None))
      case Some(subId) =>
        Async[F].blocking(Subscription.retrieve(subId)).map(extractFromSubscription)
    }

  override def setCancelAtPeriodEnd(subscriptionId: String, cancel: Boolean): F[StripeSubState] =
    for {
      _ <- init
      updated <- Async[F].blocking {
        val params = SubscriptionUpdateParams
          .builder()
          .setCancelAtPeriodEnd(java.lang.Boolean.valueOf(cancel))
          .build()

        val updatedSubscription = {
          val sub = Subscription.retrieve(subscriptionId)
          sub.update(params)
        }
        updatedSubscription
      }
      (_, status, currentEnd, cancelFlag) = extractFromSubscription(updated)
      state = StripeSubState(
        status = status.getOrElse("active"),
        currentPeriodEnd = currentEnd,
        cancelAtPeriodEnd = cancelFlag.getOrElse(cancel)
      )
      _ <- Logger[F].info(s"[Stripe] setCancelAtPeriodEnd sub=$subscriptionId cancel=$cancel -> status=${state.status} end=${state.currentPeriodEnd}")
    } yield state

}

object StripeBillingService {

  def apply[F[_] : Async : Logger](appConfig: AppConfig): StripeBillingServiceAlgebra[F] = new StripeBillingServiceImpl[F](appConfig)
}
