package cache

import cats.effect.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*

import scala.concurrent.duration.*

// trait RedisCacheAlgebra[F[_]] {

//   // def redisResource: Resource[F, RedisCommands[F, String, String]]

//   def storeSession(redis: RedisCommands[F, String, String], token: String, userId: String): F[Unit]

//   def validateSession(redis: RedisCommands[F, String, String], token: String): F[Option[String]]
// }

// class RedisCache[F[_] : Async](appConfig: AppConfig) extends RedisCacheAlgebra[F] {

//   def redisResource: Resource[F, RedisCommands[F, String, String]] = {
//     val redisUri = sys.env.getOrElse("REDIS_HOST", appConfig.localConfig.postgresqlConfig.host)
//     Redis[F].utf8(s"redis://$redisUri:6379")
//   }

//   override def storeSession(redis: RedisCommands[F, String, String], token: String, userId: String): F[Unit] =
//     redis.setEx(s"auth:session:$token", userId, 1.hour)

//   override def validateSession(redis: RedisCommands[F, String, String], token: String): F[Option[String]] =
//     redis.get(s"auth:session:$token")
// }


trait RedisCacheAlgebra[F[_]] {

  def storeSession(token: String, userId: String): F[Unit]
  
  def validateSession(token: String): F[Option[String]]
}


class RedisCache[F[_]: Async](appConfig: AppConfig) extends RedisCacheAlgebra[F] {
  private val redisUri = sys.env.getOrElse("REDIS_HOST", appConfig.localConfig.postgresqlConfig.host)

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] =
    Redis[F].utf8(s"redis://$redisUri:6379").use(fa)

  def storeSession(token: String, userId: String): F[Unit] =
    withRedis(_.setEx(s"auth:session:$token", userId, 1.hour))

  def validateSession(token: String): F[Option[String]] =
    withRedis(_.get(s"auth:session:$token"))
}
