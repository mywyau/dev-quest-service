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
import models.users.CreateUserData

trait RegistrationControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class RegistrationControllerImpl[F[_] : Async : Concurrent : Logger](
  registrationService: RegistrationServiceAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with RegistrationControllerAlgebra[F] {

  implicit val createRegistrationDecoder: EntityDecoder[F, CreateUserData] = jsonOf[F, CreateUserData]

  private def extractBearerToken(req: Request[F]): Option[String] =
    req.headers.get[headers.Authorization].map(_.value.stripPrefix("Bearer "))

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(tokenFromRedis) if tokenFromRedis == token =>
        onValid
      case Some(_) =>
        Logger[F].info("[RegistrationControllerImpl][withValidSession] User session does not match reusered user session token value from redis.")
        Forbidden("User session does not match reusered user session token value from redis.")
      case None =>
        Logger[F].info("[RegistrationControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "registration" / "health" =>
      Logger[F].info(s"[BaseControllerImpl] GET - Health check for backend RegistrationController service") *>
        Ok(GetResponse("dev-quest-service/registration/health", "I am alive").asJson)

    // case req @ GET -> Root / "registration" / "data" / userId =>
    //   extractSessionToken(req) match {
    //     case Some(headerToken) =>
    //       withValidSession(userId, headerToken) {
    //         Logger[F].info(s"[RegistrationController] GET - Authenticated for userId $userId") *>
    //           registrationService.getUser(userId).flatMap {
    //             case Some(user) =>
    //               Logger[F].info(s"[RegistrationController] GET - Found user ${user.userId.toString()}") *>
    //                 Ok(user.asJson)
    //             case None =>
    //               BadRequest(ErrorResponse("NO_QUEST", "No user found").asJson)
    //           }
    //       }
    //     case None =>
    //       Logger[F].info(s"[RegistrationController] GET - Unauthorised") *>
    //         Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
    //   }

    case req @ POST -> Root / "registration" / "data" / "create" / userId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userId, headerToken) {
            Logger[F].info(s"[RegistrationControllerImpl] POST - Creating user") *>
              req.decode[CreateUserData] { request =>
                registrationService.createUser(userId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[RegistrationControllerImpl] POST - Successfully created a user") *>
                      Created(CreatedResponse(response.toString, "user details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    // case req @ PUT -> Root / "user" / "update" / "type" / userId =>
    //   extractSessionToken(req) match {
    //     case Some(headerToken) =>
    //       withValidSession(userId, headerToken) {
    //         Logger[F].info(s"[RegistrationControllerImpl] PUT - Updating user with ID: $userId") *>
    //           req.decode[UpdateUserType] { request =>
    //             registrationService.updateUserType(userId, request.userType).flatMap {
    //               case Valid(response) =>
    //                 Logger[F].info(s"[RegistrationControllerImpl] PUT - Successfully updated user for ID: $userId") *>
    //                   Ok(UpdatedResponse(UpdateSuccess.toString, s"User $userId updated successfully with type: ${request.userType}").asJson)
    //               case Invalid(errors) =>
    //                 Logger[F].info(s"[RegistrationControllerImpl] PUT - Validation failed for user update: ${errors.toList}") *>
    //                   BadRequest(ErrorResponse(code = "VALIDATION_ERROR", message = errors.toList.mkString(", ")).asJson)
    //             }
    //           }
    //       }
    //     case None =>
    //       Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
    //   }

    // case req @ DELETE -> Root / "registration" / "data" / "delete" / userId =>
    //   extractSessionToken(req) match {
    //     case Some(headerToken) =>
    //       withValidSession(userId, headerToken) {
    //         Logger[F].info(s"[RegistrationControllerImpl] DELETE - Attempting to delete user") *>
    //           registrationService.deleteUser(userId).flatMap {
    //             case Valid(response) =>
    //               Logger[F].info(s"[RegistrationControllerImpl] DELETE - Successfully deleted user for $userId") *>
    //                 Ok(DeletedResponse(response.toString, "User deleted successfully").asJson)
    //             case Invalid(error) =>
    //               val errorResponse = ErrorResponse("placeholder error", "some deleted user message")
    //               BadRequest(errorResponse.asJson)
    //           }
    //       }
    //     case None =>
    //       Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Bearer token")
    //   }
  }
}

object RegistrationController {
  def apply[F[_] : Async : Concurrent](
    registrationService: RegistrationServiceAlgebra[F],
    sessionCache: SessionCacheAlgebra[F]
  )(implicit logger: Logger[F]): RegistrationControllerAlgebra[F] =
    new RegistrationControllerImpl[F](registrationService, sessionCache)
}
