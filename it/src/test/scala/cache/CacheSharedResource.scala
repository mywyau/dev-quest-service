package cache

import cache.RedisCache
import cache.RedisCacheAlgebra
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import configuration.models.*
import configuration.models.AppConfig
import configuration.BaseAppConfig
import controllers.TestRoutes.*
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import dev.profunktor.redis4cats.Redis
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.circe.parser
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.auth.UserSession
import models.cache.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.EntityDecoder
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import repository.DatabaseResource.postgresqlConfigResource
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext
import shared.HttpClientResource
import shared.SessionCacheResource
import weaver.GlobalResource
import weaver.GlobalWrite

object CacheSharedResource extends GlobalResource with BaseAppConfig {

  def executionContextResource: Resource[IO, ExecutionContext] =
    ExecutionContexts.fixedThreadPool(4)

  def withRedis[A](
    redisHost: String,
    redisPort: Int,
    fa: RedisCommands[IO, String, String] => IO[A]
  ): IO[A] = {
    val redisUri = s"redis://$redisHost:$redisPort"
    Logger[IO].info(s"[SessionCache] Uri: $redisUri") *>
      Redis[IO].utf8(redisUri).use(fa)
  }

  def cacheResource[A](
    redisHost: String,
    redisPort: Int,
    fa: RedisCommands[IO, String, String] => IO[A]
  ): IO[A] =
    withRedis(redisHost, redisPort, fa)

  def clientResource: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build

  def sharedResources(global: GlobalWrite): Resource[IO, Unit] =
    for {
      appConfig <- appConfigResource
      appRedisConfig <- redisConfigResource(appConfig)
      redisHost <- Resource.eval {
        IO.pure(sys.env.getOrElse("REDIS_HOST", appRedisConfig.host))
      }
      redisPort <- Resource.eval {
        IO.pure(sys.env.get("REDIS_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(appRedisConfig.port))
      }
      ce <- executionContextResource
      client <- clientResource
      sessionCache <- SessionCache.make[IO](redisHost, redisPort, appConfig)
      _ <- global.putR(HttpClientResource(client))
      _ <- global.putR(SessionCacheResource(sessionCache))
    } yield ()
}
