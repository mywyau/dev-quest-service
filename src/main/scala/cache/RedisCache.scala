package cache

import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import dev.profunktor.redis4cats.RedisCommands
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import models.auth.UserSession
import org.http4s.circe._
import org.http4s.EntityDecoder
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

trait RedisCacheAlgebra[F[_]] {

  // def getSession(userId: String): F[Option[String]]

  def getSession(userId: String): F[Option[UserSession]]

  def storeSession(userId: String, token: String): F[Unit]

  def updateSession(userId: String, token: String): F[Unit] // NEW

  def deleteSession(userId: String): F[Long]
}

class RedisCacheImpl[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig) extends RedisCacheAlgebra[F] {

  implicit val userSessionDecoder: EntityDecoder[F, UserSession] = jsonOf[F, UserSession]

  private val uri = s"redis://$redisHost:$redisPort"

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] = {
    val redisUri = s"redis://$redisHost:$redisPort"
    Logger[F].info(s"[RedisCache] Uri: $redisUri") *>
      Redis[F].utf8(redisUri).use(fa)
  }

  def getSession(userId: String): F[Option[UserSession]] = {

    val key = s"auth:session:$userId"

    for {
      _ <- Logger[F].info(s"[RedisCache] Retrieving session for userId=$userId")
      maybeJ <- withRedis(_.get(key))
      result <- maybeJ match {
        case None =>
          Logger[F]
            .info(s"[RedisCache] No session found for userId=$userId")
            .as(None)
        case Some(jsonStr) =>
          Logger[F].info(s"[RedisCache] Session JSON for userId=$userId: $jsonStr") *>
            (
              decode[UserSession](jsonStr) match {
                case Right(session) =>
                  Logger[F]
                    .info(s"[RedisCache] Parsed session for userId=$userId")
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
    Logger[F].info(s"[RedisCache] Storing session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].info(s"[RedisCache] Session stored with TTL 1 day for userId=$userId")

  // No difference to def storeSession
  override def updateSession(userId: String, token: String): F[Unit] =
    Logger[F].info(s"[RedisCache] Updating session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].info(s"[RedisCache] Session updated with TTL 1 day for userId=$userId")

  override def deleteSession(userId: String): F[Long] =
    Logger[F].info(s"[RedisCache] Deleting session for userId=$userId") *>
      withRedis(_.del(s"auth:session:$userId")).flatTap { deleted =>
        if (deleted > 0)
          Logger[F].info(s"[RedisCache] Successfully deleted session for userId=$userId")
        else
          Logger[F].info(s"[RedisCache] No session to delete for userId=$userId")
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
