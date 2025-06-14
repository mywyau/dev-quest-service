package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cache.SessionCache
import cats.effect.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import configuration.models.*
import configuration.BaseAppConfig
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
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import repository.DatabaseResource.postgresqlConfigResource
import scala.concurrent.ExecutionContext
import shared.HttpClientResource
import shared.RedisCacheResource
import shared.SessionCacheResource
import shared.TransactorResource
import weaver.GlobalResource
import weaver.GlobalWrite

object S3SharedResources extends GlobalResource with BaseAppConfig {

  def executionContextResource: Resource[IO, ExecutionContext] =
    ExecutionContexts.fixedThreadPool(4)

  def sharedResources(global: GlobalWrite): Resource[IO, Unit] =
    for {
      appConfig <- appConfigResource
      ce <- executionContextResource
      _ <- global.putR(appConfig) // Store config for later use
    } yield ()
}
