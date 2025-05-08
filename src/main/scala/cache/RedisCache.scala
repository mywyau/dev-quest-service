package cache

import cats.effect.*
import cats.syntax.all.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

trait RedisCacheAlgebra[F[_]] {
  def storeSession(userId: String, token: String): F[Unit]
  def getSession(userId: String): F[Option[String]]
  def deleteSession(userId: String): F[Long]
}

class RedisCache[F[_]: Async: Logger](appConfig: AppConfig) extends RedisCacheAlgebra[F] {

  private val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.localConfig.redisConfig.host)
  private val redisPort = sys.env.get("REDIS_PORT").flatMap(_.toIntOption).getOrElse(appConfig.localConfig.redisConfig.port)

  private val redisUri = s"redis://$redisHost:$redisPort"

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] =
    Redis[F].utf8(redisUri).use(fa)

  override def storeSession(userId: String, token: String): F[Unit] =
    Logger[F].info(s"[RedisCache] Storing session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].debug(s"[RedisCache] Session stored with TTL 1 day for userId=$userId")

  override def getSession(userId: String): F[Option[String]] =
    Logger[F].info(s"[RedisCache] Retrieving session for userId=$userId") *>
      withRedis(_.get(s"auth:session:$userId")).flatTap {
        case Some(_) => Logger[F].debug(s"[RedisCache] Session found for userId=$userId")
        case None    => Logger[F].debug(s"[RedisCache] No session found for userId=$userId")
      }

  override def deleteSession(userId: String): F[Long] =
    Logger[F].info(s"[RedisCache] Deleting session for userId=$userId") *>
      withRedis(_.del(s"auth:session:$userId")).flatTap { deleted =>
        if (deleted > 0)
          Logger[F].debug(s"[RedisCache] Successfully deleted session for userId=$userId")
        else
          Logger[F].debug(s"[RedisCache] No session to delete for userId=$userId")
      }
}
