// controllers/PricingPlanController.scala
package controllers

import cache.SessionCacheAlgebra
import cats.effect.*
import cats.syntax.all.*
import configuration.AppConfig
import io.circe.Json
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import models.UserType
import models.pricing.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import repositories.PricingPlanRepositoryAlgebra
import repositories.UserPricingPlanRepositoryAlgebra
import services.UserPricingPlanServiceAlgebra
import services.stripe.StripeBillingServiceAlgebra

import java.util.UUID

trait PricingPlanControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

final class PricingPlanControllerImpl[F[_] : Async : Concurrent : Logger](
  appConfig: AppConfig,
  sessionCache: SessionCacheAlgebra[F],
  userPricingPlanService: UserPricingPlanServiceAlgebra[F],
  pricingPlanRepo: PricingPlanRepositoryAlgebra[F],
  userPricingPlanRepo: UserPricingPlanRepositoryAlgebra[F],
  stripeBillingService: StripeBillingServiceAlgebra[F]
) extends Http4sDsl[F]
    with PricingPlanControllerAlgebra[F] {

  // ---------- helpers ----------
  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies.find(_.name == "auth_session").map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(sess) if sess.cookieValue == token => onValid
      case Some(_) => Forbidden("Session token mismatch")
      case None => Forbidden("Invalid or expired session")
    }

  // private def withValidSession(userId: String, token: String, allowedUserTypes: Set[UserType])(onValid: F[Response[F]]): F[Response[F]] =
  //   sessionCache.getSession(userId).flatMap {
  //     case Some(userSessionJson)
  //         if userSessionJson.cookieValue == token &&
  //           allowedUserTypes.contains(UserType.fromString(userSessionJson.userType)) =>
  //       Logger[F].debug(s"[PricingPlanControllerImpl][withValidSession] Valid session for userId: $userId") *>
  //         onValid
  //     case Some(_) =>
  //       Logger[F].debug("[PricingPlanControllerImpl][withValidSession] Session token or user type mismatch.")
  //       Forbidden("User session token or type mismatch.")
  //     case None =>
  //       Logger[F].debug("[PricingPlanControllerImpl][withValidSession] Session not found or expired.")
  //       Forbidden("Invalid or expired session")
  //   }

  private def idemKey(req: Request[F]): String =
    req.headers
      .get(CIString("Idempotency-Key"))
      .map(_.head.value)
      .getOrElse(UUID.randomUUID().toString)

  final case class PlanSelect(planId: String)
  final case class UrlResponse(url: String)

  implicit val decPlanSelect: io.circe.Decoder[PlanSelect] = deriveDecoder
  implicit val encPlanSelect: io.circe.Encoder[PlanSelect] = deriveEncoder
  implicit val encUrl: io.circe.Encoder[UrlResponse] = deriveEncoder

  implicit val entityDecPlanSelect: EntityDecoder[F, PlanSelect] = jsonOf[F, PlanSelect]
  implicit val entityEncUrl: EntityEncoder[F, UrlResponse] = jsonEncoderOf[F, UrlResponse]
  implicit val encPlanRow: EntityEncoder[F, PricingPlanRow] = jsonEncoderOf[F, PricingPlanRow]
  implicit val encPlanRows: EntityEncoder[F, List[PricingPlanRow]] = jsonEncoderOf[F, List[PricingPlanRow]]
  implicit val encPlanSnapshot: EntityEncoder[F, PlanSnapshot] = jsonEncoderOf[F, PlanSnapshot]
  implicit val encPlanView: EntityEncoder[F, UserPricingPlanView] = jsonEncoderOf[F, UserPricingPlanView]

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "billing" / "plans" / userId =>
      extractSessionToken(req) match {
        case Some(token) =>
          withValidSession(userId, token) {
            Logger[F].debug(s"[PricingPlanControllerImpl][/billing/me/plan/$userId] Successfully validated/authed user and grabbing plan snapshot") *> (
              for {
                maybeUserType: Option[String] <- sessionCache.getSession(userId).map(_.map(_.userType))
                result <-
                  maybeUserType match {
                    case Some(userType) =>
                      Logger[F].debug(s"[PricingPlanControllerImpl][/billing/me/plan/$userId] Successfully retrieved the pricing plans for the user:$userId - $userType") *>
                        pricingPlanRepo.listPlans(UserType.fromString(userType)).flatMap(Ok(_))
                    case _ =>
                      Forbidden("Missing / Unknown user Type")
                  }

              } yield result
            )
          }
        case None =>
          Forbidden("Missing session")
      }

    // 2) Get current user's plan snapshot (cached)
    // GET /billing/me/plan/:userId
    case req @ GET -> Root / "billing" / "me" / "plan" / userId =>
      extractSessionToken(req) match {
        case Some(token) =>
          withValidSession(userId, token) {
            Logger[F].debug(s"[PricingPlanControllerImpl][My Plan] Successfully validated/authed user and grabbing plan snapshot - $userId") *>
              userPricingPlanService.getSnapshot(userId).flatMap(Ok(_))
          }
        case None =>
          Logger[F].debug(s"[PricingPlanControllerImpl][My Plan] Forbidden to validated/auth user and grabbing plan snapshot - $userId") *>
            Forbidden("Missing session")
      }

    // 3) Select a plan:
    //    - free → switch immediately (returns the upserted row as JSON)
    //    - paid → create Stripe Checkout (returns { "url": ... })
    //
    // POST /billing/checkout/:userId
    // body: { "planId": "<plan_id>" }

// inside `routes`:

    case req @ POST -> Root / "billing" / "checkout" / userId =>
      extractSessionToken(req) match {
        case Some(token) =>
          withValidSession(userId, token) {
            val idem = idemKey(req)
            (for {
              _ <- Logger[F].info(s"[PricingPlanControllerImpl][Checkout] start user=$userId idem=$idem")
              body <- req.as[PlanSelect].flatTap(b => Logger[F].debug(s"[PricingPlanControllerImpl][Checkout] payload planId=${b.planId} user=$userId idem=$idem"))
              planOp <- pricingPlanRepo.byPlanId(body.planId)
              plan <- Async[F]
                .fromOption(
                  planOp,
                  new IllegalArgumentException(s"Unknown planId ${body.planId}")
                )
                .flatTap(p =>
                  Logger[F].info(
                    s"[PricingPlanControllerImpl][Checkout] plan resolved user=$userId planId=${p.planId} price=${p.price} interval=${p.interval} idem=$idem"
                  )
                )

              resp <-
                if (plan.price == 0) {
                  Logger[F].info(s"[PricingPlanControllerImpl][Checkout] free plan → switching immediately user=$userId planId=${plan.planId} idem=$idem") *>
                    userPricingPlanService
                      .switchToFree(userId, plan.planId)
                      .flatTap(_ => Logger[F].info(s"[PricingPlanControllerImpl][Checkout] switched to free user=$userId planId=${plan.planId} idem=$idem"))
                      .flatMap(row => Ok(row.asJson))
                } else {
                  Logger[F].info(s"[PricingPlanControllerImpl][Checkout] paid plan → creating Stripe session user=$userId planId=${plan.planId} idem=$idem") *>
                    userPricingPlanService
                      .createCheckout(userId, plan.planId, idem)
                      .flatTap(url => Logger[F].info(s"[PricingPlanControllerImpl][Checkout] stripe session created user=$userId idem=$idem url=$url"))
                      .flatMap(u => Ok(UrlResponse(u)))
                }

              _ <- Logger[F].info(s"[PricingPlanControllerImpl][Checkout] done user=$userId idem=$idem")
            } yield resp).handleErrorWith {
              case e: IllegalArgumentException =>
                Logger[F].warn(e)(s"[PricingPlanControllerImpl][Checkout] bad request user=$userId idem=$idem") *>
                  BadRequest(Json.obj("message" -> Json.fromString(e.getMessage)))
              case e =>
                Logger[F].error(e)(s"[PricingPlanControllerImpl][Checkout] unexpected error user=$userId idem=$idem") *>
                  InternalServerError(Json.obj("message" -> Json.fromString("Checkout failed")))
            }
          }

        case None =>
          Logger[F].warn(s"[PricingPlanControllerImpl][Checkout] missing session cookie user=$userId") *>
            Forbidden("Missing session")
      }

    case req @ POST -> Root / "billing" / "portal" / userId =>
      extractSessionToken(req) match {
        case Some(token) =>
          withValidSession(userId, token) {
            for {
              viewOpt <- userPricingPlanRepo.get(userId)
              customerId <- Sync[F].fromOption(
                viewOpt.flatMap(_.userPlanRow.stripeCustomerId),
                new IllegalArgumentException(s"No Stripe customerId for user=$userId")
              )
              returnUrl = s"${appConfig.localAppConfig.devIrlFrontendConfig.baseUrl}/billing/select-plan/client"
              url <- stripeBillingService.createBillingPortalSession(customerId, returnUrl)
              res <- Ok(UrlResponse(url))
            } yield res
          }
        case None => Forbidden("Missing session")
      }

    // 4) Open Stripe Billing Portal
    // POST /billing/portal/:userId  -> returns { url }
    // case req @ POST -> Root / "billing" / "portal" / userId =>
    //   extractSessionToken(req) match {
    //     case Some(token) =>
    //       withValidSession(userId, token) {
    //         for {
    //           viewOpt    <- userPlanRepo.get(userId)
    //           customerId <- Async[F].fromOption(
    //                           viewOpt.flatMap(_.userPlanRow.stripeCustomerId), // <-- correct field
    //                           new IllegalArgumentException("No Stripe customer id for user")
    //                         )
    //           returnUrl   = s"${appConfig.prodAppConfig.stripeConfig.stripeUrl}/billing/select-plan"
    //           url        <- stripeBilling.createBillingPortalSession(customerId, returnUrl)
    //           res        <- Ok(UrlResponse(url))
    //         } yield res
    //       }
    //     case None => Forbidden("Missing session")
    //   }

    // POST /billing/cancel/:userId  -> cancel at period end
    
    case req @ POST -> Root / "billing" / "cancel" / userId =>
      extractSessionToken(req) match {
        case Some(token) =>
          withValidSession(userId, token) {
            Logger[F].debug(s"[PricingPlanControllerImpl][Cancel] Attempting to cancel pricing plan") *>
              userPricingPlanService.cancelAtPeriodEnd(userId) *>
              Logger[F].debug(s"[PricingPlanControllerImpl][Cancel] Successfully cancelled pricing plan") *>
              Ok()
          }
        case None =>
          Forbidden("Missing session")
      }

    // POST /billing/resume/:userId -> undo cancellation (optional)
    case req @ POST -> Root / "billing" / "resume" / userId =>
      extractSessionToken(req) match {
        case Some(token) =>
          withValidSession(userId, token) {
            Logger[F].debug(s"[PricingPlanControllerImpl][Resume] Attempting to cancel pricing plan") *>
              userPricingPlanService.resumeSubscription(userId) *>
              Logger[F].debug(s"[PricingPlanControllerImpl][Resume] Attempting to cancel pricing plan") *>
              Ok()
          }
        case None => Forbidden("Missing session")
      }
  }
}

object PricingPlanController {
  def apply[F[_] : Async : Concurrent](
    appConfig: AppConfig,
    sessionCache: SessionCacheAlgebra[F],
    userPlanService: UserPricingPlanServiceAlgebra[F],
    pricingPlanRepo: PricingPlanRepositoryAlgebra[F],
    userPlanRepo: UserPricingPlanRepositoryAlgebra[F],
    stripeBillingService: StripeBillingServiceAlgebra[F]
  )(implicit logger: Logger[F]): PricingPlanControllerAlgebra[F] =
    new PricingPlanControllerImpl[F](appConfig, sessionCache, userPlanService, pricingPlanRepo, userPlanRepo, stripeBillingService)
}
