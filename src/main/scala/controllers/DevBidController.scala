package controllers

import cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.Json
import io.circe.syntax.EncoderOps
import models.*
import models.database.UpdateSuccess
import models.dev_bids.DevBid
import models.dev_bids.DevBidCount
import models.responses.*
import org.http4s.*
import org.http4s.Challenge
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import services.DevBidServiceAlgebra

import scala.concurrent.duration.*

trait DevBidControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class DevBidControllerImpl[F[_] : Async : Concurrent : Logger](
  sessionCache: SessionCacheAlgebra[F],
  devBidService: DevBidServiceAlgebra[F]
) extends Http4sDsl[F]
    with DevBidControllerAlgebra[F] {

  implicit val devBidTypeDecoder: EntityDecoder[F, DevBid] = jsonOf[F, DevBid]

  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].debug("[DevBidControllerImpl][withValidSession] Found valid session for userId:") *>
          onValid
      case Some(_) =>
        Logger[F].debug("[DevBidControllerImpl][withValidSession] User session does not match requested user session token value from redis.")
        Forbidden("User session does not match requested user session token value from redis.")
      case None =>
        Logger[F].debug("[DevBidControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "dev" / "bid" / "health" =>
      Logger[F].debug(s"[DevBidControllerImpl] GET - Health check for backend DevBidController service") *>
        Ok(GetResponse("dev-quest-service/registration/health", "I am alive - DevBidController").asJson)

    case req @ GET -> Root / "dev" / "bid" / devId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devId, cookieToken) {
            Logger[F].debug(s"[DevBidControllerImpl] GET - Authenticated for devId $devId, attempting to retrieve bid for quest: $questId") *>
              devBidService.getBid(questId).flatMap {
                case Some(devBid) =>
                  Logger[F].debug(s"[DevBidControllerImpl] GET - Found bid ${devId.toString()}") *>
                    Ok(devBid.asJson)
                case None =>
                  BadRequest(ErrorResponse("NO_BID", "No bid found").asJson)
              }
          }
        case None =>
          Logger[F].debug(s"[DevBidControllerImpl] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ GET -> Root / "dev" / "bids" / "count" / clientId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            Logger[F].debug(s"[DevBidControllerImpl] GET - Trying to get count for dev bids") *>
              devBidService.countBids(questId).flatMap { case numberOfQuests =>
                Logger[F].debug(s"[DevBidControllerImpl] GET - Total number of dev bids: ${numberOfQuests}") *>
                  Ok(DevBidCount(numberOfQuests).asJson)
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ GET -> Root / "dev" / "bids" / clientId / questId :? PageParam(maybePage) +& LimitParam(maybeLimit) =>
      val page = maybePage.getOrElse(1)
      val limit = maybeLimit.getOrElse(20)
      val offset = (page - 1) * limit

      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            Logger[F].debug(s"[SkillController] GET - Paginated bid data for quest: $questId (offset=$offset, limit=$limit)") *>
              devBidService.getDevBids(questId, limit, offset).flatMap { case data =>
                Logger[F].debug(s"[SkillController] GET - Retrieved bid data: $data") *>
                  Ok(data.asJson) // works for 0 bids
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "dev" / "bid" / "upsert" / devId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devId, cookieToken) {
            Logger[F].debug(s"[DevBidControllerImpl] PUT - Updating bid data for devId: $devId") *>
              req.decode[DevBid] { request =>
                devBidService.upsertBid(devId, questId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[DevBidControllerImpl] PUT - Successfully updated user type for ID: $devId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"Bid updated for user $devId successfully for quest: ${questId}").asJson)
                  case Invalid(errors) =>
                    val apiErrors = errors.toList.map(ApiError.from)
                    Logger[F].debug(s"[DevBidControllerImpl] PUT - Unable to upsert bid: $apiErrors") *>
                      BadRequest(apiErrors.asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }
  }
}

object DevBidController {
  def apply[F[_] : Async : Concurrent](
    sessionCache: SessionCacheAlgebra[F],
    devBidService: DevBidServiceAlgebra[F]
  )(implicit logger: Logger[F]): DevBidControllerAlgebra[F] =
    new DevBidControllerImpl[F](sessionCache, devBidService)
}
