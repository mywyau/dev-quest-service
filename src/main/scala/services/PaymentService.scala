package services

import cats.data.Validated.Valid
import cats.effect.*
import cats.effect.Concurrent
import cats.effect.IO
import cats.implicits.*
import com.stripe.net.Webhook
import configuration.AppConfig
import io.circe.Json
import io.circe.parser
import io.circe.syntax.EncoderOps
import io.github.cdimascio.dotenv.Dotenv
import models.payment.CheckoutSessionUrl
import models.payment.StripePaymentIntent
import models.responses.*
import org.http4s.*
import org.http4s.Header
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.syntax.all.uri
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.Logger
import repositories.QuestRepositoryAlgebra
import repositories.RewardRepositoryAlgebra

trait PaymentServiceAlgebra[F[_]] {

// Not used for mvp
  def createQuestPayment(
    questId: String,
    clientId: String,
    developerStripeId: String,
    amountCents: Long
  ): F[StripePaymentIntent]

// Not used for mvp
  def handleStripeWebhook(payload: String, sigHeader: String): F[Unit]

  def createCheckoutSession(
    questId: String,
    clientId: String,
    developerStripeId: String,
    amountCents: Long
  ): F[CheckoutSessionUrl]
}

class LivePaymentService[F[_] : Async : Logger](
  stripePaymentService: StripePaymentService[F],
  questRepo: QuestRepositoryAlgebra[F],
  rewardRepo: RewardRepositoryAlgebra[F]
) extends PaymentServiceAlgebra[F] {

  override def createQuestPayment(
    questId: String,
    clientId: String,
    developerStripeId: String,
    amountCents: Long
  ): F[StripePaymentIntent] =
    for {
      _ <- Logger[F].debug(s"[LivePaymentService][createQuestPayment] Creating payment intent for quest [$questId] by client [$clientId]")
      _ <- questRepo.validateOwnership(questId, clientId)
      intent <- stripePaymentService.createPaymentIntent(
        amount = amountCents,
        currency = "usd",
        devAccountId = developerStripeId
      )
      _ <- Logger[F].debug(s"[LivePaymentService][createQuestPayment] Stripe intent created for quest [$questId]")
    } yield intent

  override def handleStripeWebhook(payload: String, sigHeader: String): F[Unit] =
    for {
      json <- stripePaymentService.verifyWebhook(payload, sigHeader)
      eventType = json.hcursor.get[String]("type").getOrElse("unknown")
      _ <- Logger[F].debug(s"[LivePaymentService][handleStripeWebhook] Stripe webhook received: $eventType")

      _ <- eventType match {
        case "payment_intent.succeeded" =>
          val maybeQuestId = json.hcursor
            .downField("data")
            .downField("object")
            .downField("metadata")
            .get[String]("questId")
            .toOption

          maybeQuestId match {
            case Some(questId) =>
              for {
                _ <- Logger[F].debug(s"[LivePaymentService][handleStripeWebhook] Payment succeeded for quest [$questId]")
                _ <- questRepo.markPaid(questId)
              } yield ()
            case None =>
              Logger[F].warn("[LivePaymentService][handleStripeWebhook] No questId found in payment metadata")
          }

        case other =>
          Logger[F].debug(s"[LivePaymentService][handleStripeWebhook] Ignoring webhook type: $other")
      }
    } yield ()

  override def createCheckoutSession(
    questId: String,
    clientId: String,
    developerStripeId: String,
    amountCents: Long
  ): F[CheckoutSessionUrl] = for {
    _ <- Logger[F].debug(s"[LivePaymentService][createCheckoutSession] Creating checkout session for quest [$questId] by client [$clientId]")
    _ <- questRepo.validateOwnership(questId, clientId)
    _ <- Logger[F].debug(s"[LivePaymentService][createCheckoutSession] Passed ownership validation check [$questId] by client [$clientId]")
    session <- stripePaymentService.createCheckoutSession(
      questId = questId,
      clientId = clientId,
      developerStripeId = developerStripeId,
      amount = amountCents,
      currency = "usd"
    )
  } yield session

}
