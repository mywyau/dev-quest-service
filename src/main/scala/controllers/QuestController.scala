package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import io.circe.syntax.EncoderOps
import models.quests.CreateQuestPartial
import models.quests.UpdateQuestPartial
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.ErrorResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import services.QuestServiceAlgebra

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

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "quest" / "user" / userIdFromRoute =>
      req.headers.get[headers.Authorization].map(_.value.stripPrefix("Bearer ")) match {
        case Some(token) =>
          // redisCache.redisResource.use { client =>
            redisCache.validateSession(token).flatMap {
              case Some(userIdFromSession) if userIdFromSession == userIdFromRoute =>
                Logger[F].info(s"[QuestControllerImpl] GET - Quest details for userId: $userIdFromSession") *>
                  questService.getByUserId(userIdFromSession).flatMap {
                    case Some(quest) =>
                      Ok(quest.asJson)
                    case None =>
                      BadRequest(ErrorResponse("NO_QUEST", "No quest found").asJson)
                  }
              case Some(_) =>
                Forbidden("Session user does not match requested userId.")
              case None =>
                Forbidden("Invalid or expired session")
            }
          // }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }

    case GET -> Root / "quest" / questId =>
      Logger[F].info(s"[QuestControllerImpl] GET - Quest details for questId: $questId") *>
        questService.getByQuestId(questId).flatMap {
          case Some(quest) =>
            Logger[F].info(s"[QuestControllerImpl] GET - Successfully retrieved quest") *>
              Ok(quest.asJson)
          case _ =>
            val errorResponse = ErrorResponse("error", "error codes")
            BadRequest(errorResponse.asJson)
        }

    case req @ POST -> Root / "quest" / "create" =>
      Logger[F].info(s"[QuestControllerImpl] POST - Creating quest") *>
        req.decode[CreateQuestPartial] { request =>
          questService.create(request).flatMap {
            case Valid(response) =>
              Logger[F].info(s"[QuestControllerImpl] POST - Successfully created a quest") *>
                Created(CreatedResponse(response.toString, "quest details created successfully").asJson)
            case Invalid(_) =>
              InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
          }
        }

    case req @ PUT -> Root / "quest" / "update" / questId =>
      Logger[F].info(s"[QuestControllerImpl] PUT - Updating quest with ID: $questId") *>
        req.decode[UpdateQuestPartial] { request =>
          questService.update(questId, request).flatMap {
            case Valid(response) =>
              Logger[F].info(s"[QuestControllerImpl] PUT - Successfully updated quest for ID: $questId") *>
                Ok(UpdatedResponse(response.toString, "quest updated successfully").asJson)
            case Invalid(errors) =>
              Logger[F].warn(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
          }
        }

    case DELETE -> Root / "quest" / questId =>
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
}

object QuestController {
  def apply[F[_] : Async : Concurrent](questService: QuestServiceAlgebra[F], redisCache: RedisCacheAlgebra[F])(implicit logger: Logger[F]): QuestControllerAlgebra[F] =
    new QuestControllerImpl[F](questService, redisCache)
}
