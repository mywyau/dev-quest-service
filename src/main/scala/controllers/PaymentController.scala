// file: routes/PaymentRoutes.scala

package controllers

import cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.*
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.*
import io.circe.Json
import io.circe.syntax.*
import io.circe.syntax.EncoderOps
import models.Completed
import models.Failed
import models.InProgress
import models.NotStarted
import models.QuestStatus
import models.Review
import models.Submitted
import models.database.UpdateSuccess
import models.payment.CheckoutPaymentPayload
import models.quests.*
import models.responses.*
import org.http4s.*
import org.http4s.Challenge
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import services.PaymentServiceAlgebra

import scala.concurrent.duration.*

trait PaymentControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class PaymentControllerImpl[F[_] : Async : Concurrent : Logger](
  paymentService: PaymentServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with PaymentControllerAlgebra[F] {

  implicit val createDecoder: EntityDecoder[F, CheckoutPaymentPayload] = jsonOf[F, CheckoutPaymentPayload]

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].debug("[QuestControllerImpl][withValidSession] Found valid session for userId:") *>
          onValid
      case Some(_) =>
        Logger[F].debug("[QuestControllerImpl][withValidSession] User session does not match requested user session token value from redis.")
        Forbidden("User session does not match requested user session token value from redis.")
      case None =>
        Logger[F].debug("[QuestControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "payment" / "health" =>
      Logger[F].debug(s"[PaymentController] GET - Health check for backend PaymentController") *>
        Ok(GetResponse("/dev-quest-service/payment/health", "I am alive - PaymentController").asJson)

    // Client triggers this to pay a developer for a quest - Not used
    case req @ POST -> Root / "pay" / clientId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            for {
              json <- req.as[Json]
              // devId <- questService
              developerStripeId <- json.hcursor.get[String]("developerStripeId").liftTo[F]
              amountCents <- json.hcursor.get[Long]("amountCents").liftTo[F]

              // 🔐 You may want to extract clientId from session/auth middleware
              // clientId = "mock-client-id-for-now"

              result <- paymentService.createQuestPayment(
                questId = questId,
                clientId = clientId,
                developerStripeId = developerStripeId,
                amountCents = amountCents
              )
              resp <- {
                Logger[F].debug(s"[PaymentController] POST - pay a developer for a quest - Not used") *>
                  Ok(result.asJson)
              }
            } yield resp
          }
        case None =>
          Logger[F].debug("[PaymentController] Unauthorized request to /pay/clientId/questId") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }

    // Stripe sends webhooks here  - Not used
    case req @ POST -> Root / "stripe" / "webhook" =>
      for {
        payload <- req.as[String]
        sigHeader <- req.headers
          .get(CIString("Stripe-Signature"))
          .map(_.head.value)
          .liftTo[F](new Exception("Missing Stripe-Signature header"))
        _ <- paymentService.handleStripeWebhook(payload, sigHeader)
        resp <- Ok("Webhook handled")
      } yield resp

    // Stripe Checkout we use this payment option
    case req @ POST -> Root / "stripe" / "checkout" / clientId / questId =>
        extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            for {
              payload <- req.as[CheckoutPaymentPayload]
              session <- {
                Logger[F].debug(s"[PaymentController] Attempting to create Stripe checkout session, payload: $payload") *>
                  paymentService.createCheckoutSession(
                    questId,
                    clientId,
                    payload.developerStripeId,
                    payload.amountCents
                  )
              }
              resp <- {
                Logger[F].debug("[PaymentController] Unauthorized request to /pay/clientId/questId") *>
                  Ok(session.asJson)
              }
            } yield resp
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing auth cookie")
      }
  }
}
