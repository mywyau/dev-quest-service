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
import models.stripe.*
import org.http4s.*
import org.http4s.Header
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.syntax.all.uri
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.Logger
import repositories.QuestRepositoryAlgebra
import repositories.RewardRepositoryAlgebra

class StripePaymentService[F[_] : Async : Logger](
  appConfig: AppConfig,
  client: Client[F]
) {

  // fall back is .env not app config but in prod/infra we use aws secrets for the sys variables
  val secretKey: String = 
    if (appConfig.featureSwitches.localTesting) {
      sys.env.getOrElse("STRIPE_TEST_SECRET_KEY", Dotenv.load().get("STRIPE_TEST_SECRET_KEY"))          
    } else  {
      sys.env.getOrElse("STRIPE_TEST_SECRET_KEY", "")
    }

  val webhookSecret: String = 
    if (appConfig.featureSwitches.localTesting) {
      sys.env.getOrElse("STRIPE_TEST_WEBHOOK_SECRET", Dotenv.load().get("STRIPE_TEST_WEBHOOK_SECRET"))          
    } else  {
      sys.env.getOrElse("STRIPE_TEST_WEBHOOK_SECRET", "")
    }  
    
  private val baseUri: Uri = 
    Uri.fromString(appConfig.localAppConfig.stripeConfig.stripeUrl)
      .getOrElse(sys.error(s"Invalid Stripe URL: ${appConfig.localAppConfig.stripeConfig.stripeUrl}"))

  val platformFeePercent: BigDecimal =
    sys.env
      .get("STRIPE_PLATFORM_FEE_PERCENT")
      .flatMap(s => Either.catchOnly[NumberFormatException](BigDecimal(s)).toOption)
      .getOrElse(appConfig.localAppConfig.stripeConfig.platformFeePercent)

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
      "success_url" -> "http://localhost:3000/payment/success",
      "cancel_url" -> "http://localhost:3000/payment/error"
    )

    val req = Request[F](
      method = Method.POST,
      uri = baseUri / "checkout" / "sessions"
    ).withHeaders(authHeader)
      .withEntity(form)

    for {
      _ <- Logger[F].debug(s"[StripeClient][createCheckoutSession] Starting Checkout Session creation")
      _ <- Logger[F].debug(s"[StripeClient][createCheckoutSession] Quest ID: $questId, Client ID: $clientId, Dev Stripe ID: $developerStripeId")
      _ <- Logger[F].debug(s"[StripeClient][createCheckoutSession] Amount (cents): $amount, Fee (cents): $fee, Currency: $currency")
      _ <- Logger[F].debug(s"[StripeClient][createCheckoutSession] Requesting Stripe Checkout Session with form data: ${form.values.mkString(", ")}")

      responseJson <- client.run(req).use { resp =>
          resp.as[Json].flatMap { json =>
            if (resp.status.isSuccess) {
              json.hcursor.get[String]("url") match {
                case Right(url) =>
                  Logger[F].debug(s"[StripeClient] Checkout Session created: $url") *>
                    Sync[F].pure(CheckoutSessionUrl(url))
                case Left(err) =>
                  Logger[F].error(s"[StripeClient] Missing 'url' in response: ${json.spaces2}") *>
                    Sync[F].raiseError[CheckoutSessionUrl](new RuntimeException("Missing 'url' in Stripe response"))
              }
            } else {
              Logger[F].error(s"[StripeClient] Checkout failed with ${resp.status.code}: ${json.spaces2}") *>
                Sync[F].raiseError[CheckoutSessionUrl](new RuntimeException(s"Stripe error ${resp.status.code}"))
            }
          }
      }
      _ <- Logger[F].debug(s"[StripeClient] Checkout Session created successfully: ${responseJson.asJson.spaces2}")

      url <- responseJson.asJson.hcursor.get[String]("url").liftTo[F].handleErrorWith { err =>
        Logger[F].error(s"[StripeClient] Failed to extract 'url' from response: ${err.getMessage}") *> err.raiseError[F, String]
      }

    } yield CheckoutSessionUrl(url)
  }
}
