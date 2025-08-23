package controllers

import infrastructure.cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.*
import models.database.*
import models.estimation_expirations.EstimationExpiration
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.EstimationExpirationServiceAlgebra

trait EstimationExpirationControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class EstimationExpirationControllerImpl[F[_] : Async : Concurrent : Logger](
  sessionCache: SessionCacheAlgebra[F],
  estimationExpirationService: EstimationExpirationServiceAlgebra[F]
) extends Http4sDsl[F]
    with EstimationExpirationControllerAlgebra[F] {

  implicit val estimationExpirationDecoder: EntityDecoder[F, EstimationExpiration] = jsonOf[F, EstimationExpiration]

  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].debug("[EstimationExpirationControllerImpl][withValidSession] Found valid session for userId:") *>
          onValid
      case Some(_) =>
        Logger[F].debug("[EstimationExpirationControllerImpl][withValidSession] User session does not match requested user session token value from redis.")
        Forbidden("User session does not match requested user session token value from redis.")
      case None =>
        Logger[F].debug("[EstimationExpirationControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "dev" / "expiration" / "health" =>
      Logger[F].debug(s"[EstimationExpirationControllerImpl] GET - Health check for backend EstimationExpirationController service") *>
        Ok(GetResponse("dev-quest-service/registration/health", "I am alive - EstimationExpirationController").asJson)

    case req @ GET -> Root / "estimation" / "expiration" / userId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userId, cookieToken) {
            Logger[F].debug(s"[EstimationExpirationControllerImpl] GET - Authenticated for userId $userId, attempting to retrieve expiration for quest: $questId") *>
              estimationExpirationService.getExpiration(questId).flatMap {
                case Some(expiration) =>
                  Logger[F].debug(s"[EstimationExpirationControllerImpl] GET - Found Estimation Expiration ${expiration}") *>
                    Ok(expiration.asJson)
                case None =>
                  BadRequest(ErrorResponse("NO_EXPIRATION", "No expiration found").asJson)
              }
          }
        case None =>
          Logger[F].debug(s"[EstimationExpirationControllerImpl] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    // case req @ GET -> Root / "dev" / "bids" / clientId / questId :? PageParam(maybePage) +& LimitParam(maybeLimit) =>
    //   val page = maybePage.getOrElse(1)
    //   val limit = maybeLimit.getOrElse(20)
    //   val offset = (page - 1) * limit

    //   extractSessionToken(req) match {
    //     case Some(cookieToken) =>
    //       withValidSession(clientId, cookieToken) {
    //         Logger[F].debug(s"[SkillController] GET - Paginated bid data for quest: $questId (offset=$offset, limit=$limit)") *>
    //           estimationExpirationService.getEstimationExpirations(questId, limit, offset).flatMap { case data =>
    //             Logger[F].debug(s"[SkillController] GET - Retrieved bid data: $data") *>
    //               Ok(data.asJson) // works for 0 bids
    //           }
    //       }
    //     case None =>
    //       Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
    //   }

    // case req @ PUT -> Root / "dev" / "bid" / "upsert" / devId / questId =>
    //   extractSessionToken(req) match {
    //     case Some(cookieToken) =>
    //       withValidSession(devId, cookieToken) {
    //         Logger[F].debug(s"[EstimationExpirationControllerImpl] PUT - Updating bid data for devId: $devId") *>
    //           req.decode[EstimationExpiration] { request =>
    //             estimationExpirationService.upsertBid(devId, questId, request).flatMap {
    //               case Valid(response) =>
    //                 Logger[F].debug(s"[EstimationExpirationControllerImpl] PUT - Successfully updated user type for ID: $devId") *>
    //                   Ok(UpdatedResponse(UpdateSuccess.toString, s"Bid updated for user $devId successfully for quest: ${questId}").asJson)
    //               case Invalid(errors) =>
    //                 val apiErrors = errors.toList.map(ApiError.from)
    //                 Logger[F].debug(s"[EstimationExpirationControllerImpl] PUT - Unable to upsert bid: $apiErrors") *>
    //                   BadRequest(apiErrors.asJson)
    //             }
    //           }
    //       }
    //     case None =>
    //       Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
    //   }
  }
}

object EstimationExpirationController {
  def apply[F[_] : Async : Concurrent](
    sessionCache: SessionCacheAlgebra[F],
    estimationExpirationService: EstimationExpirationServiceAlgebra[F]
  )(implicit logger: Logger[F]): EstimationExpirationControllerAlgebra[F] =
    new EstimationExpirationControllerImpl[F](sessionCache, estimationExpirationService)
}
