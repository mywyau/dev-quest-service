package services

import cats.data.Validated.Valid
import cats.effect.*
import cats.effect.Concurrent
import cats.effect.IO
import cats.implicits.*
import com.stripe.net.Webhook
import configuration.models.AppConfig
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
  stripeClient: StripeClient[F],
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
      _ <- Logger[F].info(s"Creating payment intent for quest [$questId] by client [$clientId]")
      _ <- questRepo.validateOwnership(questId, clientId)
      intent <- stripeClient.createPaymentIntent(
        amount = amountCents,
        currency = "usd",
        devAccountId = developerStripeId
      )
      _ <- Logger[F].info(s"Stripe intent created for quest [$questId]")
    } yield intent

  override def handleStripeWebhook(payload: String, sigHeader: String): F[Unit] =
    for {
      json <- stripeClient.verifyWebhook(payload, sigHeader)
      eventType = json.hcursor.get[String]("type").getOrElse("unknown")
      _ <- Logger[F].info(s"Stripe webhook received: $eventType")

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
                _ <- Logger[F].info(s"Payment succeeded for quest [$questId]")
                _ <- questRepo.markPaid(questId)
              } yield ()
            case None =>
              Logger[F].warn("No questId found in payment metadata")
          }

        case other =>
          Logger[F].info(s"Ignoring webhook type: $other")
      }
    } yield ()

  override def createCheckoutSession(
    questId: String,
    clientId: String,
    developerStripeId: String,
    amountCents: Long
  ): F[CheckoutSessionUrl] = for {
    _ <- Logger[F].info(s"Creating checkout session for quest [$questId] by client [$clientId]")
    _ <- questRepo.validateOwnership(questId, clientId)
    _ <- Logger[F].info(s"Passed ownership validation check [$questId] by client [$clientId]")
    session <- stripeClient.createCheckoutSession(
      questId = questId,
      clientId = clientId,
      developerStripeId = developerStripeId,
      amount = amountCents,
      currency = "usd"
    )
  } yield session

}
