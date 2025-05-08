package cache

import cats.effect.*
import cats.syntax.all.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

trait RedisCacheAlgebra[F[_]] {

  def getSession(userId: String): F[Option[String]]

  def storeSession(userId: String, token: String): F[Unit]

  def deleteSession(userId: String): F[Long]
}

class RedisCacheImpl[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig) extends RedisCacheAlgebra[F] {

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] = {
    val redisUri = s"redis://$redisHost:$redisPort"
    Logger[F].info(s"[RedisCache] Uri: $redisUri") *>
      Redis[F].utf8(redisUri).use(fa)
  }

  override def getSession(userId: String): F[Option[String]] =
    Logger[F].info(s"[RedisCache] Retrieving session for userId=$userId") *>
      withRedis(_.get(s"auth:session:$userId")).flatTap {
        case Some(_) => Logger[F].info(s"[RedisCache] Session found for userId=$userId")
        case None => Logger[F].info(s"[RedisCache] No session found for userId=$userId")
      }

  override def storeSession(userId: String, token: String): F[Unit] =
    Logger[F].info(s"[RedisCache] Storing session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].info(s"[RedisCache] Session stored with TTL 1 day for userId=$userId")

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
