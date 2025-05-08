package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cats.effect.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import configuration.BaseAppConfig
import configuration.models.*
import controllers.TestRoutes.*
import dev.profunktor.redis4cats.Redis
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repository.DatabaseResource.postgresqlConfigResource
import shared.HttpClientResource
import shared.RedisCacheResource
import shared.TransactorResource
import weaver.GlobalResource
import weaver.GlobalWrite

import scala.concurrent.ExecutionContext

object ControllerSharedResource extends GlobalResource with BaseAppConfig {

  def executionContextResource: Resource[IO, ExecutionContext] =
    ExecutionContexts.fixedThreadPool(4)

  def transactorResource(postgresqlConfig: PostgresqlConfig, ce: ExecutionContext): Resource[IO, HikariTransactor[IO]] =
    HikariTransactor.newHikariTransactor[IO](
      driverClassName = "org.postgresql.Driver",
      url = s"jdbc:postgresql://${postgresqlConfig.host}:${postgresqlConfig.port}/${postgresqlConfig.dbName}",
      user = postgresqlConfig.username,
      pass = postgresqlConfig.password,
      connectEC = ce
    )

  def clientResource: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build

  def serverResource(
    host: Host,
    port: Port,
    router: Resource[IO, HttpRoutes[IO]]
  ): Resource[IO, Server] =
    router.flatMap { router =>
      EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withHttpApp(router.orNotFound)
        .build
    }

  def sharedResources(global: GlobalWrite): Resource[IO, Unit] =
    for {
      appConfig <- configResource
      host <- hostResource(appConfig)
      port <- portResource(appConfig)
      postgresqlConfig <- postgresqlConfigResource(appConfig)
      postgresqlHost <- Resource.eval {
        IO.pure(sys.env.getOrElse("DB_HOST", postgresqlConfig.host))
      }
      postgresqlPort <- Resource.eval {
        IO.pure(sys.env.get("DB_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(postgresqlConfig.port))
      }
      appRedisConfig <- redisConfigResource(appConfig)
      redisHost <- Resource.eval {
        IO.pure(sys.env.getOrElse("REDIS_HOST", appRedisConfig.host))
      }
      redisPort <- Resource.eval {
        IO.pure(sys.env.get("REDIS_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(appRedisConfig.port))
      }
      ce <- executionContextResource
      xa <- transactorResource(postgresqlConfig.copy(host = postgresqlHost, port = postgresqlPort), ce)
      redis <- RedisCache.make[IO](redisHost, redisPort, appConfig)
      client <- clientResource
      _ <- serverResource(host, port, createTestRouter(xa, appConfig))
      _ <- global.putR(TransactorResource(xa))
      _ <- global.putR(HttpClientResource(client))
      _ <- global.putR(RedisCacheResource(redis))
    } yield ()
}
