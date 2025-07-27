package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.Json
import io.circe.syntax.EncoderOps
import models.Client
import models.Completed
import models.Dev
import models.EstimateClosed
import models.EstimateOpen
import models.Failed
import models.InProgress
import models.NotStarted
import models.Open
import models.UserType
import models.database.UpdateSuccess
import models.estimate.*
import models.responses.*
import org.http4s.*
import org.http4s.Challenge
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import services.EstimateServiceAlgebra

import scala.concurrent.duration.*

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

  private def withValidSession(userId: String, token: String, allowedUserTypes: Set[UserType])(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson)
          if userSessionJson.cookieValue == token &&
            allowedUserTypes.contains(UserType.fromString(userSessionJson.userType)) =>
        Logger[F].debug(s"[EstimateController][withValidSession] Valid session for userId: $userId") *>
          onValid
      case Some(_) =>
        Logger[F].debug("[EstimateController][withValidSession] Session token or user type mismatch.")
        Forbidden("User session token or type mismatch.")
      case None =>
        Logger[F].debug("[EstimateController][withValidSession] Session not found or expired.")
        Forbidden("Invalid or expired session")
    }
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "estimate" / "health" =>
      Logger[F].debug(s"[EstimateController] GET - Health check for backend EstimateController service") *>
        Ok(GetResponse("/dev-estimate-service/health", "I am alive - EstimateController").asJson)

    case req @ GET -> Root / "estimates" / devId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devId, cookieToken, Set(Dev, Client)) {
            Logger[F].debug(s"[EstimateController][/estimates/devId/questId] GET - Authenticated for userId: $devId") *>
              estimateService.getEstimates(questId).flatMap {
                case response @ GetEstimateResponse(EstimateOpen, Nil) =>
                  Logger[F].debug(s"[EstimateController][/estimates/devId/questId] GET - EstimateOpen, 0 estimates returned") *>
                    Ok(response.asJson)
                case response @ GetEstimateResponse(EstimateClosed, calculatedEstimate) =>
                  Logger[F].debug(s"[EstimateController][/estimates/devId/questId] GET - Found estimate ${response.toString()}") *>
                    Ok(response.asJson)
                case response @ GetEstimateResponse(EstimateOpen, calculatedEstimate) if calculatedEstimate.size < 3 =>
                  Logger[F].debug(s"[EstimateController][/estimates/devId/questId] GET - Found estimate ${response.toString()}") *>
                    Ok(response.asJson)
                case _ =>
                  BadRequest(ErrorResponse("NO_ESTIMATES", "No estimates found").asJson)
              }
          }
        case None =>
          Logger[F].debug(s"[EstimateController][/quest/userId/questId] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    // case req @ PUT -> Root / "estimates" / "finalise" / devId / questId =>
    //   extractSessionToken(req) match {
    //     case Some(cookieToken) =>
    //       withValidSession(devId, cookieToken, Set(Dev, Client)) {
    //         Logger[F].debug(s"[EstimateController][/estimates/finalise/devId/questId] PUT - Authenticated for userId: $devId") *>
    //           estimateService.finalizeQuestEstimation(questId).flatMap {
    //             case Valid(response) =>
    //               Logger[F].debug(s"[EstimateController][/estimates/finalise/devId/questId] PUT - Successfully finalised quest") *>
    //                 Ok(UpdatedResponse(UpdateSuccess.toString, s"successfully finalised quest").asJson)
    //             case _ =>
    //               BadRequest(ErrorResponse("UNABLE_TO_FINALISE_ESTIMATES", "UNABLE_TO_FINALISE_ESTIMATES_MESSAGE").asJson)
    //           }
    //       }
    //     case None =>
    //       Logger[F].debug(s"[EstimateController][/estimates/finalise/devId/questId] PUT - Unauthorised") *>
    //         Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
    //   }

    case req @ POST -> Root / "estimate" / "create" / devId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devId, cookieToken, Set(Dev)) {
            Logger[F].debug(s"[EstimateController] POST - Creating estimate") *>
              req.decode[CreateEstimate] { request =>
                Logger[F].debug(request.toString()) *>
                  estimateService.createEstimate(devId, request).flatMap {
                    case Valid(response) =>
                      Logger[F].debug(s"[EstimateController] POST - Successfully created a estimate") *>
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

// Why they’re coupled in .createEstimate(). - there is a lot of validation happening here unfortunetly.
// The user action is:

// “Dev tries to submit an estimate for a quest”

// That single action needs to validate multiple things at once:

// Validation	Why it must happen now
// Dev hasn’t exceeded daily estimate cap	Global rule for XP fairness
// Dev hasn’t estimated this quest before	Enforced at DB (fail-fast)
// Quest hasn't already been finalized	Estimation phase might be closed
// Estimate triggers quest finalization	Estimate might be the 5th needed one
// XP should be awarded after finalization	That final estimate might close the process

// They all naturally live in one method because that’s the core event handler for "submitting an estimate".
