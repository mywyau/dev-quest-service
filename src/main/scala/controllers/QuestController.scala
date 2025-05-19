package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.Json
import io.circe.syntax.EncoderOps
import models.quests.CreateQuestPartial
import models.quests.UpdateQuestPartial
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.ErrorResponse
import models.responses.GetResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.Challenge
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import services.QuestServiceAlgebra

import scala.concurrent.duration.*
import models.database.UpdateSuccess

trait QuestControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class QuestControllerImpl[F[_] : Async : Concurrent : Logger](
  questService: QuestServiceAlgebra[F],
  redisCache: RedisCacheAlgebra[F]
) extends Http4sDsl[F]
    with QuestControllerAlgebra[F] {

  implicit val createDecoder: EntityDecoder[F, CreateQuestPartial] = jsonOf[F, CreateQuestPartial]
  implicit val updateDecoder: EntityDecoder[F, UpdateQuestPartial] = jsonOf[F, UpdateQuestPartial]

  private def extractBearerToken(req: Request[F]): Option[String] =
    req.headers.get[headers.Authorization].map(_.value.stripPrefix("Bearer "))

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    redisCache.getSession(userId).flatMap {
      case Some(tokenFromRedis) if tokenFromRedis == token =>
        onValid
      case Some(_) =>
        Logger[F].info("[QuestControllerImpl][withValidSession] User session does not match requested user session token value from redis.")
        Forbidden("User session does not match requested user session token value from redis.")
      case None =>
        Logger[F].info("[QuestControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "health" =>
      Logger[F].info(s"[BaseControllerImpl] GET - Health check for backend QuestController service") *>
        Ok(GetResponse("/dev-quest-service/health", "I am alive").asJson)

    case req @ GET -> Root / "quest" / "stream" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userIdFromRoute, headerToken) {
            val page = req.params.get("page").flatMap(_.toIntOption).getOrElse(1)
            val limit = req.params.get("limit").flatMap(_.toIntOption).getOrElse(10)
            val offset = (page - 1) * limit

            Logger[F].info(
              s"[QuestController] Streaming paginated quests for $userIdFromRoute (page=$page, limit=$limit)"
            ) *>
              Ok(
                questService
                  .streamByUserId(userIdFromRoute, limit, offset)
                  .map(_.asJson.noSpaces) // Quest ⇒ JSON string
                  .evalTap(json => Logger[F].info(s"[QuestController] → $json")) // <── log every line
                  .intersperse("\n") // ND-JSON framing
                  .handleErrorWith { e =>
                    Stream.eval(Logger[F].info(e)("[QuestController] Stream error")) >> Stream.empty
                  }
                  .onFinalize(Logger[F].info("[QuestController] Stream completed").void)
              )
          }

        case None =>
          Logger[F].info("[QuestController] Unauthorized request to /quest/stream") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }

    // TODO: change this to return a list of paginated quests
    case req @ GET -> Root / "quest" / "all" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userIdFromRoute, headerToken) {
            Logger[F].info(s"[QuestController] GET - Authenticated for userId $userIdFromRoute") *>
              questService.getAllQuests(userIdFromRoute).flatMap {
                case Nil => BadRequest(ErrorResponse("NO_QUEST", "No quests found").asJson)
                case quests => Ok(quests.asJson)
              }
          }

        case None =>
          Logger[F].info(s"[QuestController] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ GET -> Root / "quest" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userIdFromRoute, headerToken) {
            Logger[F].info(s"[QuestController] GET - Authenticated for userId $userIdFromRoute") *>
              questService.getByQuestId(questId).flatMap {
                case Some(quest) => Ok(quest.asJson)
                case None => BadRequest(ErrorResponse("NO_QUEST", "No quest found").asJson)
              }
          }
        case None =>
          Logger[F].info(s"[QuestController] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ POST -> Root / "quest" / "create" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userIdFromRoute, headerToken) {
            Logger[F].info(s"[QuestControllerImpl] POST - Creating quest") *>
              req.decode[CreateQuestPartial] { request =>
                questService.create(request, userIdFromRoute).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[QuestControllerImpl] POST - Successfully created a quest") *>
                      Created(CreatedResponse(response.toString, "quest details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "update" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userIdFromRoute, headerToken) {
            Logger[F].info(s"[QuestControllerImpl] PUT - Updating quest with ID: $questId") *>
              req.decode[UpdateQuestPartial] { request =>
                questService.update(questId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[QuestControllerImpl] PUT - Successfully updated quest for ID: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"Quest $questId updated successfully").asJson)
                  case Invalid(errors) =>
                    Logger[F].info(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ DELETE -> Root / "quest" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userIdFromRoute, headerToken) {
            Logger[F].info(s"[QuestControllerImpl] DELETE - Attempting to delete quest") *>
              questService.delete(questId).flatMap {
                case Valid(response) =>
                  Logger[F].info(s"[QuestControllerImpl] DELETE - Successfully deleted quest for $questId") *>
                    Ok(DeletedResponse(response.toString, "quest deleted successfully").asJson)
                case Invalid(error) =>
                  val errorResponse = ErrorResponse("placeholder error", "some deleted quest message")
                  BadRequest(errorResponse.asJson)
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }
  }
}

object QuestController {
  def apply[F[_] : Async : Concurrent](questService: QuestServiceAlgebra[F], redisCache: RedisCacheAlgebra[F])(implicit logger: Logger[F]): QuestControllerAlgebra[F] =
    new QuestControllerImpl[F](questService, redisCache)
}
