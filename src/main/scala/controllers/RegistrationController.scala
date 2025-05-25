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

  implicit val UpdateUserTypeDecoder: EntityDecoder[F, UpdateUserType] = jsonOf[F, UpdateUserType]
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
            Logger[F].info(s"[RegistrationController] POST - Creating user") *>
              req.decode[CreateUserData] { request =>
                registrationService.createUser(userId, request).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[RegistrationController] POST - Successfully created a user") *>
                      Created(CreatedResponse(response.toString, "user details created successfully").asJson)
                  case Invalid(_) =>
                    InternalServerError(ErrorResponse(code = "Code", message = "An error occurred").asJson)
                }
              }
          }
        case None =>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), "Missing Cookie")
      }

    case req @ PUT -> Root / "registration" / "update" / "type" / userId =>
      extractSessionToken(req) match {
        case Some(headerToken) =>
          withValidSession(userId, headerToken) {
            Logger[F].info(s"[RegistrationController] PUT - Updating user with ID: $userId") *>
              req.decode[UpdateUserType] { request =>
                registrationService.updateUserType(userId, request.userType).flatMap {
                  case Valid(response) =>
                    Logger[F].info(s"[RegistrationController] PUT - Successfully updated user for ID: $userId") *>
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

      // Fe26.2*1*449440b58ec88f44a8cee7deb35d2a70c09d0172523e35138bd8f8a022b44986*asjmNa2KlXJtTZqQk7yZpQ*F1zX0Hd8ph6BEVs1f67dbVLRd86Of7S2KVGDrhbAAGz-hceTfuUDAO43JRgGl_6svzRCT0sh8UMN6wGLZ-hxOhDaGYOypHzPmqreeVjYYD67mP04K3a8MFTaBNvrAnqBca4Lpcj41KPP-AcPAZSU1BXDSkrMQsM6iDhwvB3Hv70oqsHDdD8lIkApxxGCw9Osjppl5VT8aQM8ZeavfyYnl3tjrngfAPyMKAeYLzhOIhhMObkmpsUG5rK1AZYce3YMX9H5mgeqW1nWg_r6S7qFlW7GKB0eiiGtalNaH5XcRDyrzrRfA1yEciNiUSaOIAx-bDcdp_ChN5C9GVd1IKt0c4MkcGV_NvfgZqn8D-PsYRScF6fSB02r2HgmepPMjIGh-O5YZf3oCEhT2DAf1onMzSJWBlOPzx-FHRTG30Qt9BLP9ZY1dyZKe6yJjZ1Eh7Yzts6F60Sh1XVJI9S7NYSz_w*1748208676402*b99119f71f3e7e286aeb93311f47e9dc897d6d097b2ee039539159b44bc63d23*8FDQjuJddBDjuOy1udZRrkI_uPMnDdIyioMSd11csoY~2
      // Fe26.2*1*09ad9f92c21566924d1cf7c47fc5ff299a287b0c738ab1f0ca7efb342e4f2b88*UtURut0cqWKdK2FxBuGXAw*r61cr2j3j1-Hh8bPEl2GJy7pHPE-G4zWNM2kqzefvmC6Z2OXR0POPok48lnOBKeH6obwyfUiM5uKl0gLVgekJoo16bBhZBxGU9mDXM493DHqu133DV9UU9MXKJQqyWtdOHYQWaQO_Ye-2-CDNjIIXlhPTlHXVoMxWvJ5-if4flCXlGBa9lOUe1wLHjIaGv4bn2l3l-z5hSawpSS4P3nG1kY4aO922JrNe5gCXwvP5oQW-KzKYeQB-wOfsozBM04uUlHiHXODPyUM4k7dtSt6k9mjE36SOPqrW3X5AduATKz8TDqMzT8-4hPv78xaMERJFJBo3FC4mW6K6j6XCOQ0_6Nc-DHngp3J_d1_PrnjzMixuf6UEXysBb0uO3gULhMBowNaO2BN-tEYiCLm-D8OC_MyHb_RdYWYSWB9vzyrVZ2Q8Zb8bkw0Bqh3K-9qyIoy3PjDauEDCjpVJG0EQ4xxJQ*1748209108693*f1e4889b7da45a622b582004205b1160652f86af7cef6f7e14a0a9f737780e56*FQQLd_zGoMiiI9pi_cKLse4yx5fT3iy7fMNnJOJiQfU~2
      // Fe26.2*1*09ad9f92c21566924d1cf7c47fc5ff299a287b0c738ab1f0ca7efb342e4f2b88*UtURut0cqWKdK2FxBuGXAw*r61cr2j3j1-Hh8bPEl2GJy7pHPE-G4zWNM2kqzefvmC6Z2OXR0POPok48lnOBKeH6obwyfUiM5uKl0gLVgekJoo16bBhZBxGU9mDXM493DHqu133DV9UU9MXKJQqyWtdOHYQWaQO_Ye-2-CDNjIIXlhPTlHXVoMxWvJ5-if4flCXlGBa9lOUe1wLHjIaGv4bn2l3l-z5hSawpSS4P3nG1kY4aO922JrNe5gCXwvP5oQW-KzKYeQB-wOfsozBM04uUlHiHXODPyUM4k7dtSt6k9mjE36SOPqrW3X5AduATKz8TDqMzT8-4hPv78xaMERJFJBo3FC4mW6K6j6XCOQ0_6Nc-DHngp3J_d1_PrnjzMixuf6UEXysBb0uO3gULhMBowNaO2BN-tEYiCLm-D8OC_MyHb_RdYWYSWB9vzyrVZ2Q8Zb8bkw0Bqh3K-9qyIoy3PjDauEDCjpVJG0EQ4xxJQ*1748209108693*f1e4889b7da45a622b582004205b1160652f86af7cef6f7e14a0a9f737780e56*FQQLd_zGoMiiI9pi_cKLse4yx5fT3iy7fMNnJOJiQfU~2
      

    // case req @ DELETE -> Root / "registration" / "data" / "delete" / userId =>
    //   extractSessionToken(req) match {
    //     case Some(headerToken) =>
    //       withValidSession(userId, headerToken) {
    //         Logger[F].info(s"[RegistrationController] DELETE - Attempting to delete user") *>
    //           registrationService.deleteUser(userId).flatMap {
    //             case Valid(response) =>
    //               Logger[F].info(s"[RegistrationController] DELETE - Successfully deleted user for $userId") *>
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
