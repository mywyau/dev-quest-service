package infrastructure

import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import cats.NonEmptyParallel
import com.comcast.ip4s.*
import configuration.AppConfig
import configuration.ConfigReader
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import fs2.Stream
import middleware.Middleware.throttleMiddleware
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Origin
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.GZip
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.middleware.ResponseLogger
import org.http4s.server.Router
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Uri
import org.typelevel.ci.CIString
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import repositories.*
import routes.AuthRoutes.*
import routes.HiscoreRoutes.*
import routes.PricingPlanRoutes.pricingPlanRoutes
import routes.PricingPlanRoutes.stripeBillingWebhookRoutes
import routes.RegistrationRoutes.*
import routes.Routes.*
import routes.UploadRoutes.*
import scala.concurrent.duration.*
import scala.concurrent.duration.DurationInt
import services.*
import services.kafka.consumers.QuestCreatedConsumer
import services.kafka.producers.*

object HttpServer {

  final case class ServerConfig(
    host: String,
    port: Int,
    idleTimeout: FiniteDuration = 75.seconds,
    shutdownTimeout: FiniteDuration = 5.seconds,
    maxConnections: Int = 1024,
    enableAccessLog: Boolean = true,
    enableGzip: Boolean = true
  )

  def make[F[_] : Async : Logger](
    config: ServerConfig,
    routes: org.http4s.HttpRoutes[F]
  ): Resource[F, Unit] = {

    // Convert host/port strings to ip4s types
    val hostResource: Resource[F, Host] =
      Resource.eval(
        Host
          .fromString(config.host)
          .liftTo[F](new RuntimeException(s"Invalid host: ${config.host}"))
      )

    val portResource: Resource[F, Port] =
      Resource.eval(
        Port
          .fromInt(config.port)
          .liftTo[F](new RuntimeException(s"Invalid port: ${config.port}"))
      )

    val httpApp: HttpApp[F] = {
      val base = routes.orNotFound

      val withLogging: HttpApp[F] =
        if (config.enableAccessLog)
          RequestLogger.httpApp(logHeaders = true, logBody = false)(
            ResponseLogger.httpApp(logHeaders = false, logBody = false)(base)
          )
        else base

      if (config.enableGzip) GZip(withLogging) else withLogging
    }

    for {
      host <- hostResource
      port <- portResource
      _ <- EmberServerBuilder
        .default[F]
        .withHost(host)
        .withPort(port)
        .withHttpApp(httpApp)
        .withIdleTimeout(config.idleTimeout)
        .withShutdownTimeout(config.shutdownTimeout)
        .withMaxConnections(config.maxConnections)
        .build
        .void
    } yield ()
  }
}
