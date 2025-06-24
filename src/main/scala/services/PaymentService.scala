package services

import cats.data.Validated.Valid
import cats.effect.*
import cats.effect.Concurrent
import cats.effect.IO
import cats.implicits.*
import com.stripe.net.Webhook
import configuration.models.AppConfig
import io.circe.parser
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.payment.CheckoutSessionUrl
import models.payment.StripePaymentIntent
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.syntax.all.uri
import org.http4s.Header
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

class StripeClient[F[_] : Async : Logger](
  appConfig: AppConfig,
  client: Client[F]
) {

  private val baseUri: Uri = 
    Uri.fromString(appConfig.localConfig.stripeConfig.stripeUrl)
      .getOrElse(sys.error(s"Invalid Stripe URL: ${appConfig.localConfig.stripeConfig.stripeUrl}"))

  val secretKey: String = sys.env.getOrElse("STRIPE_SECRET_KEY", appConfig.localConfig.stripeConfig.secretKey)
  val webhookSecret: String = sys.env.getOrElse("STRIPE_WEBHOOK_SECRET", appConfig.localConfig.stripeConfig.webhookSecret)

  val platformFeePercent: BigDecimal =
    sys.env
      .get("STRIPE_PLATFORM_FEE_PERCENT")
      .flatMap(s => Either.catchOnly[NumberFormatException](BigDecimal(s)).toOption)
      .getOrElse(appConfig.localConfig.stripeConfig.platformFeePercent)

  private def authHeader: Header.Raw =
    Header.Raw(ci"Authorization", s"Bearer ${secretKey}")

  def createPaymentIntent(
    amount: Long,
    currency: String,
    devAccountId: String
  ): F[StripePaymentIntent] = {

    val fee = (amount * platformFeePercent) / 100

    val form = UrlForm(
      "amount" -> amount.toString,
      "currency" -> currency,
      "payment_method_types[]" -> "card",
      "application_fee_amount" -> fee.toString,
      "transfer_data[destination]" -> devAccountId
    )

    val req = Request[F](
      method = Method.POST,
      uri = baseUri / "payment_intents"
    ).withHeaders(authHeader)
      .withEntity(form)

    client.expect[Json](req).flatMap { json =>
      json.hcursor.get[String]("client_secret").map(StripePaymentIntent(_)).liftTo[F]
    }
  }

  def verifyWebhook(payload: String, sig: String): F[Json] =
    for {
      event <- Sync[F].delay {
        Webhook.constructEvent(
          payload,
          sig,
          webhookSecret
        )
      }
      json <- parser.parse(payload).liftTo[F]
    } yield json


  def createCheckoutSession(
    questId: String,
    clientId: String,
    developerStripeId: String,
    amount: Long,
    currency: String
  ): F[CheckoutSessionUrl] = {

    val fee = (BigDecimal(amount) * platformFeePercent / 100).toLong

    val form = UrlForm(
      "payment_method_types[]" -> "card",
      "mode" -> "payment",
      "line_items[0][price_data][currency]" -> currency,
      "line_items[0][price_data][unit_amount]" -> amount.toString,
      "line_items[0][price_data][product_data][name]" -> s"Quest Payment: $questId",
      "line_items[0][quantity]" -> "1",
      "payment_intent_data[application_fee_amount]" -> fee.toString,
      "payment_intent_data[transfer_data][destination]" -> developerStripeId,
      "success_url" -> "http://localhost:3000/",
      "cancel_url" -> "http://localhost:3000/error"
    )

    val req = Request[F](
      method = Method.POST,
      uri = baseUri / "checkout" / "sessions"
    ).withHeaders(authHeader)
      .withEntity(form)

    for {
      _ <- Logger[F].info(s"[Stripe] Starting Checkout Session creation")
      _ <- Logger[F].info(s"[Stripe] Quest ID: $questId, Client ID: $clientId, Dev Stripe ID: $developerStripeId")
      _ <- Logger[F].info(s"[Stripe] Amount (cents): $amount, Fee (cents): $fee, Currency: $currency")
      _ <- Logger[F].info(s"[Stripe] Requesting Stripe Checkout Session with form data: ${form.values.mkString(", ")}")

      responseJson <- client.run(req).use { resp =>
          resp.as[Json].flatMap { json =>
            if (resp.status.isSuccess) {
              json.hcursor.get[String]("url") match {
                case Right(url) =>
                  Logger[F].info(s"[Stripe] Checkout Session created: $url") *>
                    Sync[F].pure(CheckoutSessionUrl(url))
                case Left(err) =>
                  Logger[F].error(s"[Stripe] Missing 'url' in response: ${json.spaces2}") *>
                    Sync[F].raiseError[CheckoutSessionUrl](new RuntimeException("Missing 'url' in Stripe response"))
              }
            } else {
              Logger[F].error(s"[Stripe] Checkout failed with ${resp.status.code}: ${json.spaces2}") *>
                Sync[F].raiseError[CheckoutSessionUrl](new RuntimeException(s"Stripe error ${resp.status.code}"))
            }
          }
      }
      _ <- Logger[F].info(s"[Stripe] Checkout Session created successfully: ${responseJson.asJson.spaces2}")

      url <- responseJson.asJson.hcursor.get[String]("url").liftTo[F].handleErrorWith { err =>
        Logger[F].error(s"[Stripe] Failed to extract 'url' from response: ${err.getMessage}") *> err.raiseError[F, String]
      }

    } yield CheckoutSessionUrl(url)
  }



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
