// services/UserPricingPlanServiceImpl.scala
package services

import infrastructure.cache.PricingPlanCacheAlgebra
import cats.Monad
import cats.NonEmptyParallel
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.syntax.all.*
import cats.syntax.all.*
import configuration.AppConfig
import fs2.Stream
import io.circe.parser.decode
import io.circe.syntax.*
import models.UserType
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.pricing.*
import models.pricing.*
import models.pricing.UserPricingPlanStatus
import models.pricing.UserPricingPlanStatus.*
import org.typelevel.log4cats.Logger
import repositories.PricingPlanRepositoryAlgebra
import repositories.UserPricingPlanRepositoryAlgebra
import services.stripe.*

import java.util.UUID

trait UserPricingPlanServiceAlgebra[F[_]] {

  def getPlan(userId: String): F[Option[UserPricingPlanView]] // DB truth (used for SSR, admin, etc.)

  def getSnapshot(userId: String): F[PlanSnapshot] // cached tiny view for gates

  def ensureDefaultPlan(userId: String, userType: UserType): F[UserPricingPlanRow]

  def switchToFree(userId: String, planId: String): F[UserPricingPlanRow] // no Stripe, validate it's a free plan

  def createCheckout(userId: String, planId: String, idemKey: String): F[String] // returns redirect URL

  def applyStripeWebhook(evt: StripeEvent): F[Unit] // upsert + cache invalidate

  def cancelAtPeriodEnd(userId: String): F[Unit]

  def resumeSubscription(userId: String): F[Unit] // optional: undo cancellation
}

class UserPricingPlanServiceImpl[F[_] : Sync : Logger](
  appConfig: AppConfig,
  pricingPlanCache: PricingPlanCacheAlgebra[F], // this is a in the same cache that will store the payment plan for the user under a different key, maybe move out
  userPricingPlanRepo: UserPricingPlanRepositoryAlgebra[F],
  pricingPlanRepo: PricingPlanRepositoryAlgebra[F],
  stripeBillingService: StripeBillingServiceAlgebra[F],
) extends UserPricingPlanServiceAlgebra[F] {

  private def cacheKey(userId: String) = s"user:$userId:plan"

  private def toSnapshot(view: UserPricingPlanView, userId: String): PlanSnapshot =
    PlanSnapshot(
      userId = userId,
      planId = view.planRow.planId,
      status = view.userPlanRow.status,
      features = view.planRow.features,
      currentPeriodEnd = view.userPlanRow.currentPeriodEnd,
      cancelAtPeriodEnd = view.userPlanRow.cancelAtPeriodEnd
    )

  private def loadAndCache(userId: String): F[PlanSnapshot] =
    userPricingPlanRepo.get(userId).flatMap {
      case Some(value) =>
        val snapshot =
          PlanSnapshot(
            userId = userId,
            planId = value.planRow.planId,
            status = value.userPlanRow.status,
            features = value.planRow.features,
            currentPeriodEnd = value.userPlanRow.currentPeriodEnd,
            cancelAtPeriodEnd = value.userPlanRow.cancelAtPeriodEnd
          )
        pricingPlanCache.storePricingPlan(cacheKey(userId), Some(snapshot)) *> snapshot.pure[F]
      case None =>
        Sync[F].raiseError(new RuntimeException(s"No plan for user $userId"))
    }

  override def getPlan(userId: String): F[Option[UserPricingPlanView]] =
    userPricingPlanRepo.get(userId)

  override def getSnapshot(userId: String): F[PlanSnapshot] =
    Logger[F].debug(s"[getSnapshot] Attempting to load Snapshot for user: ${cacheKey(userId)}") *>
    pricingPlanCache.getPricingPlan(cacheKey(userId)).flatMap {
      case Some(planSnapshot) =>
        Logger[F].debug(s"[getSnapshot] ${cacheKey(userId)} success $planSnapshot") *>
        planSnapshot.pure[F] 
      case None =>
        Logger[F].debug("[getSnapshot] failed loading from db") *>
        loadAndCache(userId)
    }

  override def ensureDefaultPlan(userId: String, userType: UserType): F[UserPricingPlanRow] =
    userPricingPlanRepo.get(userId).flatMap {
      case Some(view) => view.userPlanRow.pure[F]
      case None =>
        // find the free plan for this userType 
        pricingPlanRepo.listPlans(userType).flatMap { plans =>

          val freePlanOpt = plans.find(p => p.price == 0 && p.userType == userType) 

          freePlanOpt match {
            case Some(free) =>
              userPricingPlanRepo.upsert(
                UserPlanUpsert(
                  userId = userId,
                  planId = free.planId,
                  stripeSubscriptionId = None,
                  stripeCustomerId = None,
                  status = Active,
                  currentPeriodEnd = None
                )
              ) <* pricingPlanCache.deletePricingPlan(s"user:$userId:plan").void
            case None =>
              Sync[F].raiseError(new RuntimeException(s"No free plan configured for $userType"))
          }
        }
    }  

  override def switchToFree(userId: String, planId: String): F[UserPricingPlanRow] =
    for {
      plan <- pricingPlanRepo.byPlanId(planId).flatMap {
        case Some(p) => p.pure[F]
        case None    => Sync[F].raiseError(new IllegalArgumentException(s"Unknown planId $planId"))
      }
      _ <- if (plan.price == 0) Sync[F].unit
           else Sync[F].raiseError(new IllegalArgumentException("switchToFree only allowed for free plans"))
      up <- userPricingPlanRepo.upsert(
          UserPlanUpsert(
            userId = userId,
            planId = plan.planId,
            stripeSubscriptionId = None,
            stripeCustomerId = None,
            status = Active,
            currentPeriodEnd = None
          )
        )
      viewOpt <- userPricingPlanRepo.get(userId)
      _ <- viewOpt match {
        case Some(view) => pricingPlanCache.storePricingPlan(cacheKey(userId), Some(toSnapshot(view, userId))).void
        case None       => pricingPlanCache.deletePricingPlan(cacheKey(userId)).void // fallback
      }
    } yield up

  override def createCheckout(userId: String, planId: String, idemKey: String): F[String] = {
    
      val prefix = s"[UserPricingPlanServiceImpl][createCheckout] user=$userId planId=$planId idem=$idemKey"

      (for {
        _    <- Logger[F].info(s"[UserPricingPlanServiceImpl][createCheckout] - $prefix start")
        plan <- pricingPlanRepo
                  .byPlanId(planId)
                  .flatTap(p => Logger[F].debug(s"[UserPricingPlanServiceImpl][createCheckout] - $prefix lookup result=$p"))
                  .flatMap {
                    case Some(p) => p.pure[F]
                    case None    => Sync[F].raiseError[PricingPlanRow](new IllegalArgumentException(s"Unknown planId $planId"))
                  }
        priceId <- plan.stripePriceId match {
                     case Some(pid) if plan.price > 0 =>
                       Logger[F].debug(s"[UserPricingPlanServiceImpl][createCheckout] - $prefix resolved stripePriceId=$pid price=${plan.price} interval=${plan.interval}") *>
                       pid.pure[F]
                     case Some(_) | None =>
                       Logger[F].warn(s"[UserPricingPlanServiceImpl][createCheckout] - $prefix plan not a paid Stripe plan (price=${plan.price}, stripePriceId=${plan.stripePriceId})") *>
                       Sync[F].raiseError[String](new IllegalArgumentException("Plan is not a paid Stripe plan"))
                   }
        success  = s"${appConfig.devIrlFrontendConfig.baseUrl}/billing/payment/successful?plan=${plan.planId}"
        cancel   = s"${appConfig.devIrlFrontendConfig.baseUrl}/billing/select-plan/client"
        _       <- Logger[F].debug(s"$prefix successUrl= $success cancelUrl= $cancel")
        url     <- stripeBillingService
                     .createCheckoutSession(userId, priceId, success, cancel, idemKey)
                     .flatTap(u => Logger[F].info(s"$prefix stripe session created url=$u"))
      } yield url).handleErrorWith {
        case e: IllegalArgumentException =>
          Logger[F].warn(e)(s"[UserPricingPlanServiceImpl][createCheckout] - $prefix bad request") *> Sync[F].raiseError[String](e)
        case e =>
          Logger[F].error(e)(s"[UserPricingPlanServiceImpl][createCheckout] - $prefix unexpected error") *> Sync[F].raiseError[String](e)
      }
    }


  override def applyStripeWebhook(evt: StripeEvent): F[Unit] = {
    (for {
    // 1) Resolve userId
    userId <- evt.userIdHint match {
      case Some(u) => u.pure[F]
      case None =>
        // Optional fallback: resolve by Stripe customer id if present
        evt.customerId match {
          case Some(custId) =>
            // You need a repo helper to find the user by stripe_customer_id (shown below)
            userPricingPlanRepo.findUserIdByStripeCustomerId(custId).flatMap {
              case Some(uid) => uid.pure[F]
              case None => Sync[F].raiseError[String](new RuntimeException(s"No user for customer $custId"))
            }
          case None =>
            Sync[F].raiseError[String](new RuntimeException("Stripe event missing userId and customerId"))
        }
    }

    // 2) If we have a priceId, map it to your internal plan via the catalog (plans table)
    planOpt <- evt.priceId.traverse(pid => pricingPlanRepo.byStripePriceId(pid))

    // 3) Upsert plan or just set status depending on what we got
    _ <- planOpt.flatten match {
      case Some(plan) =>

        val statusEnum =
          evt.status
            .map(UserPricingPlanStatus.fromStripeStatus) // <-- tolerant parse
            .getOrElse(Active)

        for {
          _     <- userPricingPlanRepo.upsert(
                     UserPlanUpsert(
                       userId = userId,
                       planId = plan.planId,
                       stripeSubscriptionId = evt.subscriptionId,
                       stripeCustomerId = evt.customerId,
                       status = statusEnum,
                       currentPeriodEnd = evt.currentPeriodEnd
                     )
                   )
          view  <- userPricingPlanRepo.get(userId) // join ensures features
          _     <- view match {
                     case Some(value) => 
                      Logger[F].debug(s"[UserPricingPlanServiceImpl][applyStripeWebhook] - $value") *>
                      pricingPlanCache.storePricingPlan(cacheKey(userId), Some(toSnapshot(value, userId))).void
                     case None    => 
                      Logger[F].debug(s"[UserPricingPlanServiceImpl][applyStripeWebhook] - No value found from userPricingPlanRepo deleting from cache") *>
                      pricingPlanCache.deletePricingPlan(cacheKey(userId)).void
                   }
        } yield ()

      case None =>
        evt.status
          .map(UserPricingPlanStatus.fromStripeStatus)
          .map(_.toString)
          .fold(Sync[F].unit) { stNorm =>
            for {
              _    <- userPricingPlanRepo.setStatus(userId, stNorm, evt.currentPeriodEnd, evt.cancelAtPeriodEnd.getOrElse(false))
              view <- userPricingPlanRepo.get(userId)
              _    <- view match {
                        case Some(v) => pricingPlanCache.storePricingPlan(cacheKey(userId), Some(toSnapshot(v, userId))).void
                        case None    => pricingPlanCache.deletePricingPlan(cacheKey(userId)).void
                      }
            } yield ()
          }
    }

    _ <- Logger[F].info(
      s"[UserPricingPlanServiceImpl][applyStripeWebhook] - Applied ${evt.eventType} for user=$userId plan=${planOpt.flatten.map(_.planId).getOrElse("-")} " +
      s"status=${evt.status.getOrElse("-")} sub=${evt.subscriptionId.getOrElse("-")}"
    )
  } yield ()).handleErrorWith { e =>
    Logger[F].error(e)(s"[Billing] Failed to handle Stripe event ${evt.eventType}") *> Sync[F].unit
  }
  }

  override def cancelAtPeriodEnd(userId: String): F[Unit] =
    (for {
      // Need the user’s current subscription id + customer id
      view <- userPricingPlanRepo.get(userId).flatMap {
        case Some(v) => v.pure[F]
        case None    => Sync[F].raiseError[UserPricingPlanView](new IllegalStateException("No plan for user"))
      }
      subId <- Sync[F].fromOption(view.userPlanRow.stripeSubscriptionId, new IllegalStateException("No subscription to cancel"))
      _     <- Logger[F].info(s"[Billing] cancelAtPeriodEnd user=$userId sub=$subId")

      // Ask Stripe to cancel at period end
      // (we use your StripeBillingService to keep Stripe SDK usage in one place)
      // Implement this helper in StripeBillingService if you prefer, or inline the SDK call.
      
      newState <- stripeBillingService.setCancelAtPeriodEnd(subId, cancel = true) // returns (status, currentPeriodEnd, cancelAtPeriodEnd)

      // Update DB immediately for good UX (webhook will confirm/align later too)
      _ <- userPricingPlanRepo.setStatus(
             userId,
             newState.status, // e.g. "Active" still, but cancelAtPeriodEnd = true
             newState.currentPeriodEnd,
             cancelAtPeriodEnd = true
           )

      // Refresh cache now so gates reflect it without waiting for webhook
      fresh <- userPricingPlanRepo.get(userId)
      _     <- fresh match {
                 case Some(v) =>
                   val snap = PlanSnapshot(userId, v.planRow.planId, v.userPlanRow.status, v.planRow.features, v.userPlanRow.currentPeriodEnd, v.userPlanRow.cancelAtPeriodEnd)
                   pricingPlanCache.storePricingPlan(cacheKey(userId), Some(snap)).void
                 case None => pricingPlanCache.deletePricingPlan(cacheKey(userId)).void
               }
    } yield ()).handleErrorWith { e =>
      Logger[F].error(e)(s"[Billing] cancelAtPeriodEnd failed for user=$userId") *> Sync[F].raiseError(e)
    }

  override def resumeSubscription(userId: String): F[Unit] =
    (for {
      view <- userPricingPlanRepo.get(userId).flatMap {
        case Some(v) => v.pure[F]
        case None    => Sync[F].raiseError[UserPricingPlanView](new IllegalStateException("No plan for user"))
      }
      subId <- Sync[F].fromOption(view.userPlanRow.stripeSubscriptionId, new IllegalStateException("No subscription to resume"))
      _     <- Logger[F].info(s"[Billing] resumeSubscription user=$userId sub=$subId")

      newState <- stripeBillingService.setCancelAtPeriodEnd(subId, cancel = false)

      _ <- userPricingPlanRepo.setStatus(
             userId,
             newState.status,
             newState.currentPeriodEnd,
             cancelAtPeriodEnd = false
           )

      fresh <- userPricingPlanRepo.get(userId)
      _     <- fresh match {
                 case Some(v) =>
                   val snap = PlanSnapshot(userId, v.planRow.planId, v.userPlanRow.status, v.planRow.features, v.userPlanRow.currentPeriodEnd, v.userPlanRow.cancelAtPeriodEnd)
                   pricingPlanCache.storePricingPlan(cacheKey(userId), Some(snap)).void
                 case None => pricingPlanCache.deletePricingPlan(cacheKey(userId)).void
               }
    } yield ()).handleErrorWith { e =>
      Logger[F].error(e)(s"[Billing] resumeSubscription failed for user=$userId") *> Sync[F].raiseError(e)
    }
}

// Optional typeclass if your catalog supports price-id lookups
trait PlansCatalogByPriceId[F[_]] {
  def findByStripePrice(priceId: String): F[Option[PricingPlanRow]]
}

object UserPricingPlanService {

  def apply[F[_] : Sync : Logger](
    appConfig: AppConfig,
    pricingPlanCache: PricingPlanCacheAlgebra[F],
    pricingPlanRepo: PricingPlanRepositoryAlgebra[F],
    userPricingPlanRepo: UserPricingPlanRepositoryAlgebra[F],
    stripeBillingService: StripeBillingServiceAlgebra[F]
  ): UserPricingPlanServiceAlgebra[F] =
    new UserPricingPlanServiceImpl[F](appConfig, pricingPlanCache, userPricingPlanRepo, pricingPlanRepo, stripeBillingService)
}
