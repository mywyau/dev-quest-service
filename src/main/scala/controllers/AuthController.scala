package controllers

import cache.RedisCacheAlgebra
import cats.effect.kernel.Async
import cats.implicits.*
import io.circe.syntax.EncoderOps
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
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
      redisCache.validateSession(userId).flatMap {
        case Some(token) => Ok(s"Session token: $token")
        case None => NotFound(ErrorResponse("NOT_FOUND", s"No session for userId $userId").asJson)
      }

    case req @ POST -> Root / "auth" / "session" / userId =>
      req.as[String].flatMap { sessionToken =>
        redisCache.storeSession(sessionToken, userId) *>
          Created(CreatedResponse(userId, "Session stored").asJson)
      }

    case req @ PUT -> Root / "auth" / "session" / userId =>
      req.as[String].flatMap { newToken =>
        redisCache.storeSession(newToken, userId) *>
          Ok(UpdatedResponse(userId, "Session updated").asJson)
      }

    // case DELETE -> Root / "auth" / "session" / userId =>
    //   redisCache.deleteSession(userId) *>
    //     Ok(DeletedResponse(userId, "Session deleted").asJson)
  }
}

object AuthController {
  def apply[F[_] : Async](redisCache: RedisCacheAlgebra[F])(implicit logger: Logger[F]): AuthControllerAlgebra[F] =
    new AuthControllerImpl[F](redisCache)
}
