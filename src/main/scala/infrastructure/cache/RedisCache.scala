package infrastructure.cache

import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import configuration.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import models.auth.UserSession
import org.http4s.EntityDecoder
import org.http4s.circe.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

trait RedisCacheAlgebra[F[_]] {

  def getSession(userId: String): F[Option[UserSession]]

  def storeSession(userId: String, token: String): F[Unit]

  def updateSession(userId: String, token: String): F[Unit]

  def deleteSession(userId: String): F[Long]
}

class RedisCacheImpl[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig) extends RedisCacheAlgebra[F] {

  implicit val userSessionDecoder: EntityDecoder[F, UserSession] = jsonOf[F, UserSession]

  private val uri = s"redis://$redisHost:$redisPort"

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] = {
    val redisUri = s"redis://$redisHost:$redisPort"
    Logger[F].debug(s"[RedisCache] Uri: $redisUri") *>
      Redis[F].utf8(redisUri).use(fa)
  }

  def getSession(userId: String): F[Option[UserSession]] = {

    val key = s"auth:session:$userId"

    for {
      _ <- Logger[F].debug(s"[RedisCache] Retrieving session for userId=$userId")
      maybeJ <- withRedis(_.get(key))
      result <- maybeJ match {
        case None =>
          Logger[F]
            .debug(s"[RedisCache] No session found for userId=$userId")
            .as(None)
        case Some(jsonStr) =>
          Logger[F].debug(s"[RedisCache] Session JSON for userId=$userId: $jsonStr") *>
            (
              decode[UserSession](jsonStr) match {
                case Right(session) =>
                  Logger[F]
                    .debug(s"[RedisCache] Parsed session for userId=$userId")
                    .as(Some(session))

                case Left(err) =>
                  Logger[F]
                    .error(s"[RedisCache] JSON parsing failed: $err")
                    .as(None)
              }
            )
      }
    } yield result
  }

  override def storeSession(userId: String, token: String): F[Unit] =
    Logger[F].debug(s"[RedisCache] Storing session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].debug(s"[RedisCache] Session stored with TTL 1 day for userId=$userId")

  // No difference to def storeSession
  override def updateSession(userId: String, token: String): F[Unit] =
    Logger[F].debug(s"[RedisCache] Updating session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].debug(s"[RedisCache] Session updated with TTL 1 day for userId=$userId")

  override def deleteSession(userId: String): F[Long] =
    Logger[F].debug(s"[RedisCache] Deleting session for userId=$userId") *>
      withRedis(_.del(s"auth:session:$userId")).flatTap { deleted =>
        if (deleted > 0)
          Logger[F].debug(s"[RedisCache] Successfully deleted session for userId=$userId")
        else
          Logger[F].debug(s"[RedisCache] No session to delete for userId=$userId")
      }
}

object RedisCache {

  import dev.profunktor.redis4cats.effect.Log.Stdout.given // With logs
  // import dev.profunktor.redis4cats.effect.Log.NoOp.given // No logs

  def apply[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig): RedisCacheAlgebra[F] =
    new RedisCacheImpl[F](redisHost, redisPort, appConfig)

  def make[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig): Resource[F, RedisCacheAlgebra[F]] =
    Resource.pure(apply(redisHost, redisPort, appConfig))
}
