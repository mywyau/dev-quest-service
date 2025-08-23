package controllers

import infrastructure.cache.*
import infrastructure.cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.database.UpdateSuccess
import models.responses.*
import models.rewards.*
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
import services.RewardServiceAlgebra

trait RewardControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class RewardControllerImpl[F[_] : Async : Concurrent : Logger](
  rewardService: RewardServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with RewardControllerAlgebra[F] {

  implicit val createTimeRewardDecoder: EntityDecoder[F, CreateTimeReward] = jsonOf[F, CreateTimeReward]
  implicit val createCompletionRewardDecoder: EntityDecoder[F, CreateCompletionReward] = jsonOf[F, CreateCompletionReward]
  implicit val updateRewardDataDecoder: EntityDecoder[F, UpdateRewardData] = jsonOf[F, UpdateRewardData]

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].debug("[RewardController][withValidSession] Found valid session for userId:") *>
          onValid
      case Some(_) =>
        Logger[F].debug("[RewardController][withValidSession] User session does not match rewarded user session token value from redis.")
        Forbidden("User session does not match rewarded user session token value from redis.")
      case None =>
        Logger[F].debug("[RewardController][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "reward" / "health" =>
      Logger[F].debug(s"[RewardController] GET - Health check for backend RewardController service") *>
        Ok(GetResponse("/dev-reward-service/health", "I am alive - RewardController").asJson)

    case req @ GET -> Root / "reward" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[RewardController] GET - Authenticated for userId $userIdFromRoute") *>
              rewardService.getReward(questId).flatMap {
                case Some(rewardData) =>
                  Ok(rewardData.asJson)
                case _ =>
                  BadRequest(ErrorResponse("NO_Reward", "No reward found").asJson)
              }
          }

        case None =>
          Logger[F].debug(s"[QuestController] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "reward" / "create" / "complete" / "bonus" / clientId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            Logger[F].debug(s"[RewardControllerImpl] POST - Creating complete bonus reward") *>
              req.decode[CreateCompletionReward] { reward =>
                rewardService.createCompletionReward(clientId, reward).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[RewardControllerImpl] POST - Successfully created a complete bonus reward") *>
                      Created(CreatedResponse(response.toString, "complete bonus reward details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "reward" / "create" / "time" / clientId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            Logger[F].debug(s"[RewardControllerImpl] POST - Creating time reward") *>
              req.decode[CreateTimeReward] { reward =>
                rewardService.createTimeReward(clientId, reward).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[RewardControllerImpl] POST - Successfully created a time reward") *>
                      Created(CreatedResponse(response.toString, "time reward details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "reward" / "update" / clientId / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(clientId, cookieToken) {
            Logger[F].debug(s"[RewardControllerImpl] PUT - Updating reward status for questId: $questId") *>
              req.decode[UpdateRewardData] { request =>
                rewardService.updateReward(questId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[RewardControllerImpl] PUT - Successfully updated reward status for questId: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"updated reward data: ${request.completionRewardValue} successfully, for questId: ${questId}").asJson)
                  case Invalid(errors) =>
                    Logger[F].debug(s"[RewardControllerImpl] PUT - Validation failed for reward update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }
  }
}

object RewardController {
  def apply[F[_] : Async : Concurrent](rewardService: RewardServiceAlgebra[F], sessionCache: SessionCacheAlgebra[F])(implicit logger: Logger[F]): RewardControllerAlgebra[F] =
    new RewardControllerImpl[F](rewardService, sessionCache)
}
