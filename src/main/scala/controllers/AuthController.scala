package controllers

import cache.RedisCacheAlgebra
import cats.effect.kernel.Async
import cats.implicits.*
import io.circe.syntax.*
import io.circe.syntax.EncoderOps
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

trait AuthControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class AuthControllerImpl[F[_] : Async : Logger](
  redisCache: RedisCacheAlgebra[F]
) extends Http4sDsl[F]
    with AuthControllerAlgebra[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "auth" / "session" / userId =>
      Logger[F].info(s"[AuthControllerImpl] GET - Validating session for userId: $userId") *>
        redisCache.getSession(userId).flatMap {
          case Some(token) =>
            Logger[F].info(s"[AuthControllerImpl] Session found for $userId: $token") *>
              Ok(GetResponse("200", s"Session token: $token").asJson)
          case None =>
            Logger[F].info(s"[AuthControllerImpl] No session found for $userId") *>
              NotFound(ErrorResponse("NOT_FOUND", s"No session for userId $userId").asJson)
        }

    case req @ POST -> Root / "auth" / "session" / userId =>
      Logger[F].info(s"Incoming cookies: ${req.cookies.map(c => s"${c.name}=${c.content}").mkString(", ")}")  *>
      Logger[F].info(s"POST - Creating session for userId: $userId") *>
        Async[F].delay(req.cookies.find(_.name == "auth_session")).flatMap {
          case Some(cookie) =>
            redisCache.storeSession(userId, cookie.content) *>
              Created(CreatedResponse(userId, "Session stored from cookie").asJson)
                .map(_.withContentType(`Content-Type`(MediaType.application.json)))
          case None =>
            Logger[F].info(s"No auth_session cookie for $userId") *>
              BadRequest(ErrorResponse("NO_COOKIE", "auth_session cookie not found").asJson)
                .map(_.withContentType(`Content-Type`(MediaType.application.json)))
        }

    case req @ PUT -> Root / "auth" / "session" / userId =>
      Logger[F].info(s"POST - Updating session for userId: $userId") *>
        Async[F].delay(req.cookies.find(_.name == "auth_session")).flatMap {
          case Some(cookie) =>
            redisCache.updateSession(userId, cookie.content) *>
              Ok(UpdatedResponse(userId, "Session updated from cookie").asJson)
                .map(_.withContentType(`Content-Type`(MediaType.application.json)))
          case None =>
            Logger[F].info(s"Not updated no auth_session cookie for $userId") *>
              BadRequest(ErrorResponse("NO_COOKIE", "Not updated auth_session cookie not found").asJson)
                .map(_.withContentType(`Content-Type`(MediaType.application.json)))
        }

    case DELETE -> Root / "auth" / "session" / "delete" / userId =>
      Logger[F].info(s"[AuthControllerImpl] DELETE - Deleting session for $userId") *>
        redisCache.deleteSession(userId) *>
        Ok(DeletedResponse(userId, "Session deleted").asJson)
  }
}

object AuthController {
  def apply[F[_] : Async : Logger](redisCache: RedisCacheAlgebra[F]): AuthControllerAlgebra[F] =
    new AuthControllerImpl[F](redisCache)
}
