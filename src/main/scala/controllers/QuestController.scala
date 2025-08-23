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
import models.*
import models.database.UpdateSuccess
import models.quests.*
import models.responses.*
import models.work_time.HoursOfWork
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.QuestCRUDServiceAlgebra
import services.QuestStreamingServiceAlgebra

trait QuestControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class QuestControllerImpl[F[_] : Async : Concurrent : Logger](
  questCRUDService: QuestCRUDServiceAlgebra[F],
  questStreamingService: QuestStreamingServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with QuestControllerAlgebra[F] {

  implicit val hoursOfWorkDecoder: EntityDecoder[F, HoursOfWork] = jsonOf[F, HoursOfWork]
  implicit val createDecoder: EntityDecoder[F, CreateQuestPartial] = jsonOf[F, CreateQuestPartial]
  implicit val updateDecoder: EntityDecoder[F, UpdateQuestPartial] = jsonOf[F, UpdateQuestPartial]
  implicit val updateQuestStatusPayloadDecoder: EntityDecoder[F, UpdateQuestStatusPayload] = jsonOf[F, UpdateQuestStatusPayload]
  implicit val completeQuestPayloadDecoder: EntityDecoder[F, CompleteQuestPayload] = jsonOf[F, CompleteQuestPayload]
  implicit val updateDevIdPayloadDecoder: EntityDecoder[F, AcceptQuestPayload] = jsonOf[F, AcceptQuestPayload]

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

    case req @ GET -> Root / "quest" / "health" =>
      Logger[F].debug(s"[BaseControllerImpl] GET - Health check for backend QuestController service") *>
        Ok(GetResponse("/dev-quest-service/health", "I am alive").asJson)

    case req @ GET -> Root / "quest" / "count" / "not-estimated" / "and" / "open" =>
      Logger[F].debug(s"[QuestController][/quest/count/not-estimated/and/open] GET - Trying to get count for quests with statuses not estimated or open") *>
        questCRUDService.countNotEstimatedAndOpenQuests().flatMap { case numberOfQuests =>
          Logger[F].debug(s"[QuestController][/quest/userId/questId] GET - Total number of quests with statuses not estimated or open: ${numberOfQuests}") *>
            Ok(NotEstimatedOrOpenQuestCount(numberOfQuests).asJson)
        }

    case req @ GET -> Root / "quest" / "stream" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            val page = req.params.get("page").flatMap(_.toIntOption).getOrElse(1)
            val limit = req.params.get("limit").flatMap(_.toIntOption).getOrElse(10)
            val offset = (page - 1) * limit

            Logger[F].debug(
              s"[QuestController] Streaming paginated quests for $userIdFromRoute (page=$page, limit=$limit)"
            ) *>
              Ok(
                questStreamingService
                  .streamByUserId(userIdFromRoute, limit, offset)
                  .map(_.asJson.noSpaces) // Quest ⇒ JSON string
                  .evalTap(json => Logger[F].debug(s"[QuestController] → $json")) // <── log every line
                  .intersperse("\n") // ND-JSON framing
                  .handleErrorWith { e =>
                    Stream.eval(Logger[F].debug(e)("[QuestController] Stream error")) >> Stream.empty
                  }
                  .onFinalize(Logger[F].debug("[QuestController] Stream completed").void)
              )
          }

        case None =>
          Logger[F].debug("[QuestController] Unauthorized request to /quest/stream") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }

    case req @ GET -> Root / "quest" / "stream" / "all" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            val page = req.params.get("page").flatMap(_.toIntOption).getOrElse(1)
            val limit = req.params.get("limit").flatMap(_.toIntOption).getOrElse(10)
            val offset = (page - 1) * limit

            Logger[F].debug(
              s"[QuestController] Streaming paginated quests for $userIdFromRoute (page=$page, limit=$limit)"
            ) *>
              Ok(
                questStreamingService
                  .streamAll(limit, offset)
                  .map(_.asJson.noSpaces) // Quest ⇒ JSON string
                  .evalTap(json => Logger[F].debug(s"[QuestController] → $json")) // <── log every line
                  .intersperse("\n") // ND-JSON framing
                  .handleErrorWith { e =>
                    Stream.eval(Logger[F].debug(e)("[QuestController] Stream error")) >> Stream.empty
                  }
                  .onFinalize(Logger[F].debug("[QuestController] Stream completed").void)
              )
          }

        case None =>
          Logger[F].debug("[QuestController] Unauthorized request to /quest/stream") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }

    case req @ GET -> Root / "quest" / "reward" / "stream" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            val page = req.params.get("page").flatMap(_.toIntOption).getOrElse(1)
            val limit = req.params.get("limit").flatMap(_.toIntOption).getOrElse(10)
            val offset = (page - 1) * limit

            Logger[F].debug(
              s"[QuestController] Streaming paginated quests for $userIdFromRoute (page=$page, limit=$limit)"
            ) *>
              Ok(
                questStreamingService
                  .streamAllWithRewards(limit, offset)
                  .map(_.asJson.noSpaces) // Quest ⇒ JSON string
                  .evalTap(json => Logger[F].debug(s"[QuestController] → $json")) // <── log every line
                  .intersperse("\n") // ND-JSON framing
                  .handleErrorWith { e =>
                    Stream.eval(Logger[F].debug(e)("[QuestController] Stream error")) >> Stream.empty
                  }
                  .onFinalize(Logger[F].debug("[QuestController] Stream completed").void)
              )
          }

        case None =>
          Logger[F].debug("[QuestController] Unauthorized request to /quest/stream") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
      }

    // 2) Match the query‐param pattern, *not* a literal `?status=…`
    case req @ GET -> Root / "quest" / "stream" / "dev" / "new" / devIdFromRoute :?
        StatusParam(mStatus) +&
        PageParam(mPage) +&
        LimitParam(mLimit) =>
      val status: QuestStatus = mStatus.getOrElse(InProgress) // default if absent
      val page = mPage.getOrElse(1)
      val limit = mLimit.getOrElse(10)
      val offset = (page - 1) * limit

      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(devIdFromRoute, cookieToken) {
            Logger[F].debug(
              s"[QuestController] Streaming status=$status, page=$page, limit=$limit"
            ) *>
              Ok(
                questStreamingService
                  .streamDev(devIdFromRoute, status, limit, offset)
                  .map(_.asJson.noSpaces)
                  .evalTap(json => Logger[F].debug(s"[QuestController][/quest/stream/dev/new] → $json")) // <── log every line
                  .intersperse("\n")
                  .handleErrorWith(e =>
                    Stream.eval(Logger[F].error(e)(s"[QuestController] Stream error")) >>
                      Stream.empty
                  )
                  .onFinalize(Logger[F].debug("[QuestController] Stream completed").void)
              )
          }
        case None =>
          Logger[F].warn("[QuestController] Missing auth cookie") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    // 2) Match the query‐param pattern, *not* a literal `?status=…`
    case req @ GET -> Root / "quest" / "stream" / "client" / "new" / userIdFromRoute :?
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
            Logger[F].debug(
              s"[QuestController] Streaming status=$status, page=$page, limit=$limit"
            ) *>
              Ok(
                questStreamingService
                  .streamClient(userIdFromRoute, status, limit, offset)
                  .map(_.asJson.noSpaces)
                  .evalTap(json => Logger[F].debug(s"[QuestController][/quest/stream/new] → $json")) // <── log every line
                  .intersperse("\n")
                  .handleErrorWith(e =>
                    Stream.eval(Logger[F].error(e)(s"[QuestController] Stream error")) >>
                      Stream.empty
                  )
                  .onFinalize(Logger[F].debug("[QuestController] Stream completed").void)
              )
          }
        case None =>
          Logger[F].warn("[QuestController] Missing auth cookie") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ GET -> Root / "quest" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestController][/quest/userId/questId] GET - Authenticated for userId $userIdFromRoute") *>
              questCRUDService.getByQuestId(questId).flatMap {
                case Some(quest) =>
                  Logger[F].debug(s"[QuestController][/quest/userId/questId] GET - Found quest ${quest.asJson}") *>
                    Ok(quest.asJson)
                case None =>
                  BadRequest(ErrorResponse("NO_QUEST", "No quest found").asJson)
              }
          }
        case None =>
          Logger[F].debug(s"[QuestController][/quest/userId/questId] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ POST -> Root / "quest" / "create" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestControllerImpl] POST - Creating quest") *>
              req.decode[CreateQuestPartial] { request =>
                questCRUDService.create(request, userIdFromRoute).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[QuestControllerImpl] POST - Successfully created a quest") *>
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
            Logger[F].debug(s"[QuestControllerImpl] PUT - Updating quest status with ID: $questId") *>
              req.decode[UpdateQuestStatusPayload] { request =>
                questCRUDService.updateStatus(questId, request.questStatus).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Successfully updated quest status for quest id: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"updated quest status: ${request.questStatus} successfully, for questId: ${questId}").asJson)
                  case Invalid(errors) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "accept" / "quest" / userIdFromRoute =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            req.decode[AcceptQuestPayload] { request =>
              questCRUDService.acceptQuest(request.questId, request.devId).flatMap {
                case Valid(response) =>
                  Logger[F].debug(s"[QuestControllerImpl] PUT - Successfully accepted quest quest id: ${request.questId}, by devId: ${request.devId}") *>
                    Ok(UpdatedResponse(UpdateSuccess.toString, s"updated devId: ${request.devId} successfully, for questId: ${request.questId}").asJson)
                case Invalid(errors) =>
                  Logger[F].debug(s"[QuestControllerImpl] PUT - Validation failed for user trying to accept a quest: ${errors.toList}") *>
                    BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
              }
            }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "update" / "details" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestControllerImpl] PUT - Updating quest with ID: $questId") *>
              req.decode[UpdateQuestPartial] { request =>
                questCRUDService.update(questId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Successfully updated quest for ID: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"Quest $questId updated successfully").asJson)
                  case Invalid(errors) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "update" / "complete" / "award" / "xp" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestControllerImpl] PUT - Updating quest with ID: $questId") *>
              req.decode[CompleteQuestPayload] { request =>
                questCRUDService.completeQuestAwardXp(questId, request.questStatus, request.rank).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Successfully updated quest for ID: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"Quest $questId updated successfully").asJson)
                  case Invalid(errors) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Validation failed for quest update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "quest" / "upsert" / "hours-of-work" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestControllerImpl] PUT - Upserting hours of work for quest with ID: $questId") *>
              req.decode[HoursOfWork] { request =>
                questCRUDService.upsertHoursOfWork(userIdFromRoute, questId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Successfully upserted hours of work for quest for ID: $questId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"Quest $questId updated successfully").asJson)
                  case Invalid(errors) =>
                    Logger[F].debug(s"[QuestControllerImpl] PUT - Validation failed for upserting hours of work for quest, errors: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ GET -> Root / "quest" / "hours-of-work" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestController] GET - Trying to get the hours worked for quest id: $questId") *>
              questCRUDService.getHoursOfWork(questId).flatMap {
                case Some(hoursWorked) =>
                  Logger[F].debug(s"[QuestController] GET - Successfully retrieved the hours worked for quest id: $questId") *>
                    Ok(hoursWorked.asJson)
                case _ =>
                  Logger[F].debug(s"[QuestController] GET - Unable to retrieve the hours worked for quest id: $questId") *>
                    BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = "").asJson)
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

      


    case req @ DELETE -> Root / "quest" / userIdFromRoute / questId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userIdFromRoute, cookieToken) {
            Logger[F].debug(s"[QuestControllerImpl] DELETE - Attempting to delete quest") *>
              questCRUDService.delete(questId).flatMap {
                case Valid(response) =>
                  Logger[F].debug(s"[QuestControllerImpl] DELETE - Successfully deleted quest for $questId") *>
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
  def apply[F[_] : Async : Concurrent](
    questCRUDService: QuestCRUDServiceAlgebra[F],
    questStreamingService: QuestStreamingServiceAlgebra[F],
    sessionCache: SessionCacheAlgebra[F]
  )(implicit logger: Logger[F]): QuestControllerAlgebra[F] =
    new QuestControllerImpl[F](questCRUDService, questStreamingService, sessionCache)
}
