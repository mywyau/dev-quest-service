package cache

import cats.effect.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import scala.concurrent.duration.*

trait RedisCacheAlgebra[F[_]] {

  def storeSession(userId: String, token: String): F[Unit]

  def getSession(userId: String): F[Option[String]]

  def deleteSession(userId: String): F[Long]
}

class RedisCache[F[_] : Async](appConfig: AppConfig) extends RedisCacheAlgebra[F] {
  private val redisUri = sys.env.getOrElse("REDIS_HOST", appConfig.localConfig.postgresqlConfig.host)

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] =
    Redis[F].utf8(s"redis://$redisUri:6379").use(fa)

  override def storeSession(userId: String, token: String): F[Unit] =
    withRedis(_.setEx(s"auth:session:$userId", token, 1.day))

  override def getSession(userId: String): F[Option[String]] =
    withRedis(_.get(s"auth:session:$userId"))

  override def deleteSession(userId: String): F[Long] =
    withRedis(_.del(s"auth:session:$userId"))
}
