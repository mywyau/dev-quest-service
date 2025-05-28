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
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.ErrorResponse
import models.responses.GetResponse
import models.responses.UpdatedResponse
import models.users.CreateUserData
import models.users.UpdateUserType
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.RegistrationServiceAlgebra
import services.UserDataServiceAlgebra

trait RegistrationControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class RegistrationControllerImpl[F[_] : Async : Concurrent : Logger](
  registrationService: RegistrationServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with RegistrationControllerAlgebra[F] {

  implicit val updateUserTypeDecoder: EntityDecoder[F, UpdateUserType] = jsonOf[F, UpdateUserType]
  implicit val createRegistrationDecoder: EntityDecoder[F, CreateUserData] = jsonOf[F, CreateUserData]

  private def extractBearerToken(req: Request[F]): Option[String] =
    req.headers.get[headers.Authorization].map(_.value.stripPrefix("Bearer "))

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSessionCookieOnly(userId).flatMap {
      case Some(cookieToken) if cookieToken == token =>
        onValid
      case Some(_) =>
        Logger[F].info("[RegistrationController][withValidSession] User session does not match reusered user session token value from redis.")
        Forbidden("User session does not match reusered user session token value from redis.")
      case None =>
        Logger[F].info("[RegistrationController][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "registration" / "health" =>
      Logger[F].info(s"[RegistrationController] GET - Health check for backend RegistrationController service") *>
        Ok(GetResponse("dev-quest-service/registration/health", "I am alive").asJson)

    case req @ GET -> Root/  "registration" / "user" / "data" / userId =>
      extractSessionToken(req) match {
        case Some(cookieToken) =>
          withValidSession(userId, cookieToken) {
            Logger[F].info(s"[UserDataController] GET - Authenticated for userId $userId") *>
              registrationService.getUser(userId).flatMap {
                case Some(user) =>
                  Logger[F].info(s"[UserDataController] GET - Found user ${userId.toString()}") *>
                    Ok(user.asJson)
                case None =>
                  BadRequest(ErrorResponse("NO_QUEST", "No user found").asJson)
              }
          }
        case None =>
          Logger[F].info(s"[UserDataController] GET - Unauthorised") *>
            Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ POST -> Root / "registration" / "data" / "create" / userId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userId, headerToken) {
            Logger[F].info(s"[RegistrationController] POST - Registering user") *>
              req.decode[CreateUserData] { request =>
                registrationService.createUser(userId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[RegistrationController] POST - Successfully created a user for userId: $userId") *>
                      Created(CreatedResponse(response.toString, "user details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "registration" / "update" / "user" / "type" / userId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userId, headerToken) {
            Logger[F].info(s"[RegistrationController] PUT - Updating user type for userId: $userId") *>
              req.decode[UpdateUserType] { request =>
                registrationService.updateUserType(userId, request.userType).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[RegistrationController] PUT - Successfully updated user type for ID: $userId") *>
                      Ok(UpdatedResponse(UpdateSuccess.toString, s"User $userId updated successfully with type: ${request.userType}").asJson)
                  case Invalid(errors) =>
                    Logger[F].info(s"[RegistrationController] PUT - Validation failed for user update: ${errors.toList}") *>
                      BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }
  }
}

object RegistrationController {
  def apply[F[_] : Async : Concurrent](
    registrationService: RegistrationServiceAlgebra[F],
    sessionCache: SessionCacheAlgebra[F]
  )(implicit logger: Logger[F]): RegistrationControllerAlgebra[F] =
    new RegistrationControllerImpl[F](registrationService, sessionCache)
}
