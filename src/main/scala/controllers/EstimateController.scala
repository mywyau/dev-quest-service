package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.database.UpdateSuccess
import models.estimate.*
import models.responses.*
import models.Completed
import models.Failed
import models.InProgress
import models.NotStarted
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.EstimateServiceAlgebra

trait EstimateControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class EstimateControllerImpl[F[_] : Async : Concurrent : Logger](
  estimateService: EstimateServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with EstimateControllerAlgebra[F] {

  implicit val createDecoder: EntityDecoder[F, CreateEstimate] = jsonOf[F, CreateEstimate]

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].info("[EstimateController][withValidSession] Found valid session for userId:") *>
          onValid
      case Some(_) =>
        Logger[F].info("[EstimateController][withValidSession] User session does not match reestimateed user session token value from redis.")
        Forbidden("User session does not match reestimateed user session token value from redis.")
      case None =>
        Logger[F].info("[EstimateController][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "estimate" / "health" =>
      Logger[F].info(s"[EstimateController] GET - Health check for backend EstimateController service") *>
        Ok(GetResponse("/dev-estimate-service/health", "I am alive - EstimateController").asJson)

    case req @ GET -> Root / "estimates" / devId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devId, cookieToken) {
            Logger[F].info(s"[EstimateController][/estimates/userId/questId] GET - Authenticated for userId: $devId") *>
              estimateService.getEstimates(devId, questId).flatMap {
                case Nil =>
                  BadRequest(ErrorResponse("NO_ESTIMATES", "No estimates found").asJson)
                case estimates =>
                  Logger[F].info(s"[EstimateController][/estimates/userId/questId] GET - Found estimate ${estimates.toString()}") *>
                    Ok(estimates.asJson)
              }
          }
        case None =>
          Logger[F].info(s"[EstimateController][/quest/userId/questId] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ POST -> Root / "estimate" / "create" / devId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devId, cookieToken) {
            Logger[F].info(s"[EstimateController] POST - Creating estimate") *>
              req.decode[CreateEstimate] { request =>
                estimateService.createEstimate(devId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[EstimateController] POST - Successfully created a estimate") *>
                      Created(CreatedResponse(response.toString, "estimate details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }
  }
}

object EstimateController {
  def apply[F[_] : Async : Concurrent](estimateService: EstimateServiceAlgebra[F], sessionCache: SessionCacheAlgebra[F])(implicit logger: Logger[F]): EstimateControllerAlgebra[F] =
    new EstimateControllerImpl[F](estimateService, sessionCache)
}
