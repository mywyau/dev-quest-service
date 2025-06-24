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
import repositories.StripeAccountRepositoryAlgebra

class StripeRegistrationService[F[_] : Async : Logger](
  stripeAccountRepository: StripeAccountRepositoryAlgebra[F],
  appConfig: AppConfig,
  client: Client[F]
) {

  val secretKey: String =
    sys.env.getOrElse("STRIPE_TEST_SECRET_KEY", Dotenv.load().get("STRIPE_TEST_SECRET_KEY")) // fall back is .env not app config but in prod/infra we use aws secrets for the sys variables

  val webhookSecret: String =
    sys.env.getOrElse("STRIPE_TEST_WEBHOOK_SECRET", Dotenv.load().get("STRIPE_TEST_WEBHOOK_SECRET")) // fall back is .env not app config but in prod/infra we use aws secrets for the sys variables

  println(secretKey)
  println(webhookSecret)

  private val baseUri: Uri =
    Uri
      .fromString(appConfig.localConfig.stripeConfig.stripeUrl)
      .getOrElse(sys.error(s"Invalid Stripe URL: ${appConfig.localConfig.stripeConfig.stripeUrl}"))

  println(baseUri)

  private def authHeader: Header.Raw =
    Header.Raw(ci"Authorization", s"Bearer ${secretKey}")

  private def createStripeAccount(): F[CreateStripeAccountResponse] = {
    val form = UrlForm(
      "type" -> "express",
      "country" -> "GB",
      "capabilities[card_payments][requested]" -> "true",
      "capabilities[transfers][requested]" -> "true"
    )

    val req = Request[F](
      Method.POST,
      uri = baseUri / "accounts"
    ).withHeaders(authHeader).withEntity(form)

    client.run(req).use { response =>
      response.as[Json].flatMap { json =>
        if (response.status.isSuccess) {
          for {
            _ <- Logger[F].info(s"[Stripe] Account creation response: ${json.spaces2}")
            id <- json.hcursor
              .get[String]("id")
              .leftMap(err => new RuntimeException(s"Could not extract Stripe account ID: $err\nResponse: ${json.spaces2}"))
              .liftTo[F]
          } yield CreateStripeAccountResponse(id)
        } else {
          Logger[F].error(s"[Stripe] Account creation failed: ${json.spaces2}") *>
            Async[F].raiseError(new RuntimeException(s"Stripe error: ${json.spaces2}"))
        }
      }
    }
  }

  def createAccountLink(devUserId: String)(using logger: Logger[F]): F[AccountLinkResponse] = for {
    _ <- logger.info(s"Starting account link creation for devUserId: $devUserId")

    // Step 1: Get existing or create new Stripe account
    maybeStripeAccountDetails <- stripeAccountRepository.findStripeAccount(devUserId)
    _ <- logger.debug(s"Stripe account lookup result: $maybeStripeAccountDetails")

    stripeAccountId <- maybeStripeAccountDetails match {
      case Some(stripeAccountDetails) =>
        logger.info(s"Found existing Stripe account for devUserId $devUserId: ${stripeAccountDetails.stripeAccountId}") *>
          Async[F].pure(stripeAccountDetails.stripeAccountId)

      case None =>
        for {
          _ <- logger.info(s"No Stripe account found for devUserId $devUserId. Creating a new one.")
          accountResp <- createStripeAccount()
          _ <- logger.info(s"Created new Stripe account: ${accountResp.accountId}")
          _ <- stripeAccountRepository.saveStripeAccountId(devUserId, accountResp.accountId)
          _ <- logger.debug(s"Saved Stripe account ID ${accountResp.accountId} for devUserId $devUserId")
        } yield accountResp.accountId
    }

    // Step 2: Create account onboarding link
    reqBody = UrlForm(
      "type" -> "account_onboarding",
      "account" -> stripeAccountId,
      "refresh_url" -> s"http://localhost:3000/dev/stripe/onboarding/refresh",
      "return_url" -> s"http://localhost:3000/dev/stripe/onboarding/success"
    )

    req = Request[F](
      method = Method.POST,
      uri = baseUri / "account_links"
    ).withHeaders(authHeader).withEntity(reqBody)

    _ <- logger.info(s"Sending request to Stripe to create onboarding link for account $stripeAccountId")
    response <- client.expect[Json](req)
    url <- response.hcursor
      .get[String]("url")
      .map(AccountLinkResponse(_))
      .liftTo[F]
    _ <- logger.info(s"Received account onboarding link for devUserId $devUserId")

  } yield url

  def fetchAndUpdateAccountDetails(devUserId: String): F[Unit] = for {
    maybeAccount <- stripeAccountRepository.findStripeAccount(devUserId)
    stripeAccountId <- maybeAccount match {
      case Some(details) => Async[F].pure(details.stripeAccountId)
      case None =>
        Logger[F].error(s"No Stripe account found for user $devUserId") *>
          Sync[F].raiseError[String](new RuntimeException("Missing Stripe account"))
    }

    req = Request[F](
      method = Method.GET,
      uri = baseUri / "accounts" / stripeAccountId
    ).withHeaders(authHeader)

    json <- client.expect[Json](req)

    chargesEnabled <- json.hcursor.get[Boolean]("charges_enabled").liftTo[F]
    payoutsEnabled <- json.hcursor.get[Boolean]("payouts_enabled").liftTo[F]
    detailsSubmitted <- json.hcursor.get[Boolean]("details_submitted").liftTo[F]

    _ <- stripeAccountRepository.updateStripeAccountStatus(
      devUserId,
      StripeAccountStatus(
        stripeAccountId = stripeAccountId,
        chargesEnabled = chargesEnabled,
        payoutsEnabled = payoutsEnabled,
        detailsSubmitted = detailsSubmitted
      )
    )

    _ <- Logger[F].info(s"[Stripe] Updated account status for user $devUserId")

  } yield ()

}
