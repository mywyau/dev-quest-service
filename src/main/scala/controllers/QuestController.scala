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
import models.Completed
import models.Failed
import models.InProgress
import models.NotStarted
import models.QuestStatus
import models.Review
import models.Submitted
import models.database.UpdateSuccess
import models.quests.*
import models.responses.*
import org.http4s.*
import org.http4s.Challenge
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import services.QuestServiceAlgebra

import scala.concurrent.duration.*

trait QuestControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class QuestControllerImpl[F[_] : Async : Concurrent : Logger](
  questService: QuestServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with QuestControllerAlgebra[F] {

  implicit val createDecoder: EntityDecoder[F, CreateQuestPartial] = jsonOf[F, CreateQuestPartial]
  implicit val updateDecoder: EntityDecoder[F, UpdateQuestPartial] = jsonOf[F, UpdateQuestPartial]
  implicit val updateQuestStatusPayloadDecoder: EntityDecoder[F, UpdateQuestStatusPayload] = jsonOf[F, UpdateQuestStatusPayload]
  implicit val updateDevIdPayloadDecoder: EntityDecoder[F, UpdateDevIdPayload] = jsonOf[F, UpdateDevIdPayload]

  implicit val questStatusQueryParamDecoder: QueryParamDecoder[QuestStatus] =
    QueryParamDecoder[String].emap { str =>
      Either
        .catchNonFatal(QuestStatus.fromString(str))
        .leftMap(t => ParseFailure("Invalid status", t.getMessage))
    }

  object StatusParam extends OptionalQueryParamDecoderMatcher[QuestStatus]("status")
  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].info("[QuestControllerImpl][withValidSession] Found valid session for userdId:") *>
          onValid
      case Some(_) =>
        Logger[F].info("[QuestControllerImpl][withValidSession] User session does not match requested user session token value from redis.")
        Forbidden("User session does not match requested user session token value from redis.")
      case None =>
        Logger[F].info("[QuestControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "quest" / "health" =>
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

    case req @ GET -> Root / "quest" / "stream" / "all" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            val page = req.params.get("page").flatMap(_.toIntOption).getOrElse(1)
            val limit = req.params.get("limit").flatMap(_.toIntOption).getOrElse(10)
            val offset = (page - 1) * limit

            Logger[F].info(
              s"[QuestController] Streaming paginated quests for $userIdFromRoute (page=$page, limit=$limit)"
            ) *>
              Ok(
                questService
                  .streamAll(limit, offset)
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

    // 2) Match the query‐param pattern, *not* a literal `?status=…`
    case req @ GET -> Root / "quest" / "stream" / "new" / userIdFromRoute :?
        StatusParam(mStatus) +&
        PageParam(mPage) +&
        LimitParam(mLimit) =>
      val status: QuestStatus = mStatus.getOrElse(InProgress) // default if absent
      val page = mPage.getOrElse(1)
      val limit = mLimit.getOrElse(10)
      val offset = (page - 1) * limit

      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].info(
              s"[QuestController] Streaming status=$status, page=$page, limit=$limit"
            ) *>
              Ok(
                questService
                  .stream(userIdFromRoute, status, limit, offset)
                  .map(_.asJson.noSpaces)
                  .evalTap(json => Logger[F].info(s"[QuestController][/quest/stream/new] → $json")) // <── log every line
                  .intersperse("\n")
                  .handleErrorWith(e =>
                    Stream.eval(Logger[F].error(e)(s"[QuestController] Stream error")) >>
                      Stream.empty
                  )
                  .onFinalize(Logger[F].info("[QuestController] Stream completed").void)
              )
          }
        case None =>
          Logger[F].warn("[QuestController] Missing auth cookie") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
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
                case Some(quest) =>
                  Logger[F].info(s"[QuestController] GET - Found quest ${quest.questId.toString()}") *>
                    Ok(quest.asJson)
                case None =>
                  BadRequest(ErrorResponse("NO_QUEST", "No quest found").asJson)
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

    case req @ PUT -> Root / "quest" / "update" / "status" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].info(s"[QuestControllerImpl] PUT - Updating quest with ID: $questId") *>
              req.decode[UpdateQuestStatusPayload] { request =>
                questService.updateStatus(request.questId, request.questStatus).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[QuestControllerImpl] PUT - Successfully updated quest status for quest id: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"updated quest status: ${request.questStatus} successfully, for questId: ${request.questId}").asJson)
                  case Invalid(errors) =>
                    Logger[F].info(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "update" / "dev" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            req.decode[UpdateDevIdPayload] { request =>
              questService.updateDevId(request.questId, request.devId).flatMap {
                case Valid(response) =>
                  Logger[F].info(s"[QuestControllerImpl] PUT - Successfully updated devId for quest id: ${request.devId}") *>
                    Ok(UpdatedResponse(UpdateSuccess.toString, s"updated devId: ${request.devId} successfully, for questId: ${request.questId}").asJson)
                case Invalid(errors) =>
                  Logger[F].info(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                    BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
              }
            }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "update" / "details" / userIdFromRoute / questId =>
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

    // case req @ PUT -> Root / "quest" / "update" / userIdFromRoute / questId =>
    //   extractSessionToken(req) match {
    //     case Some(headerToken) =>
    //       withValidSession(userIdFromRoute, headerToken) {
    //         Logger[F].info(s"[QuestControllerImpl] PUT - Updating quest with ID: $questId") *>
    //           req.decode[UpdateQuestPartial] { request =>
    //             questService.update(questId, request).flatMap {
    //               case Valid(response) =>
    //                 Logger[F].info(s"[QuestControllerImpl] PUT - Successfully updated quest for ID: $questId") *>
    //                   Ok(UpdatedResponse(UpdateSuccess.toString, s"Quest $questId updated successfully").asJson)
    //               case Invalid(errors) =>
    //                 Logger[F].info(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
    //                   BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
    //             }
    //           }
    //       }
    //     case None =>
    //       Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
    //   }

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
  def apply[F[_] : Async : Concurrent](questService: QuestServiceAlgebra[F], sessionCache: SessionCacheAlgebra[F])(implicit logger: Logger[F]): QuestControllerAlgebra[F] =
    new QuestControllerImpl[F](questService, sessionCache)
}

// Fe26.2*1*56c5c8dfc86cf58855e7b98c3b99676d29accef5af956670f38bdc524fc78d83*clFC7aeGyCatQ3tgWOu1OQ*vYzcBEFU8sRePAedA6uhJUW7eWCBbeZ9QHVxbP-zUf_VVl7LWRiOGvEqesvo-da2HmMh02khzS9t84KpMfrjN46X8k-8C7JMGQSGEn0GkL_mcOd3SfEmEI3rmodYioTGaYzV7U9X5YI6a--xVRYVRO2FQOElDSA6mr_e9rwUjnNlvqkbeiqjTL5HcVU34km84F1s7-1-CFwDYtr75dJpb1rXG_8hHFRFsMVNiEJjUxeSgm-_5Ev_-hIIiMgjCVNUC4ooVLEHhYUDUA6huSmVDJB3s68jq5aSQXMPhH8GVAwIgnbg9XaQO4VczfTW0x5QF9PH2YbzGwjpg7fD22XPvqb_qYyt8tOeaLLS5IyEc14RNbH6n9rzZ5GlqTr8jzunepOJjo6ayzlIlqhsjpC4vGELtXpgqCXMTczGXB-V3P5KL8M13kW0Uom5HSWqnUUhCWUNe5_sqzJ_HCIpkHDUpw*1748635615488*9363a089e85ac1f302f11c7650511f6cb7771028baa75d05c18a5b2ca70af8a8*1n9-qqvJMNHYDSHbl1cPLasWaHjNmURcgqm_A-RzMQo~2
// Fe26.2*1*56c5c8dfc86cf58855e7b98c3b99676d29accef5af956670f38bdc524fc78d83*clFC7aeGyCatQ3tgWOu1OQ*vYzcBEFU8sRePAedA6uhJUW7eWCBbeZ9QHVxbP-zUf_VVl7LWRiOGvEqesvo-da2HmMh02khzS9t84KpMfrjN46X8k-8C7JMGQSGEn0GkL_mcOd3SfEmEI3rmodYioTGaYzV7U9X5YI6a--xVRYVRO2FQOElDSA6mr_e9rwUjnNlvqkbeiqjTL5HcVU34km84F1s7-1-CFwDYtr75dJpb1rXG_8hHFRFsMVNiEJjUxeSgm-_5Ev_-hIIiMgjCVNUC4ooVLEHhYUDUA6huSmVDJB3s68jq5aSQXMPhH8GVAwIgnbg9XaQO4VczfTW0x5QF9PH2YbzGwjpg7fD22XPvqb_qYyt8tOeaLLS5IyEc14RNbH6n9rzZ5GlqTr8jzunepOJjo6ayzlIlqhsjpC4vGELtXpgqCXMTczGXB-V3P5KL8M13kW0Uom5HSWqnUUhCWUNe5_sqzJ_HCIpkHDUpw*1748635615488*9363a089e85ac1f302f11c7650511f6cb7771028baa75d05c18a5b2ca70af8a8*1n9-qqvJMNHYDSHbl1cPLasWaHjNmURcgqm_A-RzMQo~2
