import cats.effect.*
import cats.implicits.*
import cats.NonEmptyParallel
import com.auth0.jwt.algorithms.Algorithm
import com.comcast.ip4s.*
import configuration.models.AppConfig
import configuration.ConfigReader
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import middleware.JwksKeyProvider
import middleware.JwtAuth
import middleware.Middleware.throttleMiddleware
import middleware.StaticJwksKeyProvider
import org.http4s.client.middleware.Logger as ClientLogger
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.Router
import org.http4s.HttpRoutes
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import routes.Routes.*
import scala.concurrent.duration.DurationInt

object Main extends IOApp {

  implicit def logger[F[_] : Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def transactorResource[F[_] : Async](appConfig: AppConfig): Resource[F, HikariTransactor[F]] = {
    val postgresqHost =
      if (appConfig.featureSwitches.useDockerHost) {
        appConfig.localConfig.postgresqlConfig.dockerHost
      } else {
        appConfig.localConfig.postgresqlConfig.host
      }

    val dbUrl =
      s"jdbc:postgresql://$postgresqHost:${appConfig.localConfig.postgresqlConfig.port}/${appConfig.localConfig.postgresqlConfig.dbName}"

    val driverClassName = "org.postgresql.Driver"

    for {
      ce <- ExecutionContexts.fixedThreadPool(32)
      xa <- HikariTransactor.newHikariTransactor[F](
        driverClassName = driverClassName,
        url = dbUrl,
        user = appConfig.localConfig.postgresqlConfig.username,
        pass = appConfig.localConfig.postgresqlConfig.password,
        connectEC = ce
      )
    } yield xa
  }

  def createHttpRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async](
    transactor: HikariTransactor[F],
    client: Client[F],
    algorithm: Algorithm
  ): Resource[F, HttpRoutes[F]] =
    for {
      baseRoutes <- Resource.pure(baseRoutes())
      authedRoutes <- Resource.pure(JwtAuth.routesWithAuth[F](transactor, client, algorithm))

      combinedRoutes = Router(
        "/" -> baseRoutes,
        "/dev-quest-service" -> authedRoutes
      )

      corsRoutes = CORS.policy.withAllowOriginAll
        .withAllowCredentials(false)
        .withAllowHeadersAll
        .withMaxAge(1.day)
        .apply(combinedRoutes)

      throttledRoutes <- Resource.eval(throttleMiddleware(corsRoutes))
    } yield throttledRoutes

  def createServer[F[_] : Async](
    host: Host,
    port: Port,
    httpRoutes: HttpRoutes[F]
  ): Resource[F, Unit] =
    EmberServerBuilder
      .default[F]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpRoutes.orNotFound)
      .build
      .void

  override def run(args: List[String]): IO[ExitCode] = {
    val configReader = ConfigReader[IO]

    val serverResource: Resource[IO, Unit] = for {
      client <- EmberClientBuilder.default[IO].build
      keys <- Resource.eval(JwksKeyProvider.loadJwks[IO]("https://YOUR_TENANT.auth0.com/.well-known/jwks.json", client))
      keyProvider = new StaticJwksKeyProvider(keys)
      algorithm = Algorithm.RSA256(keyProvider)

      appConfig <- Resource.eval(configReader.loadAppConfig.handleErrorWith { e =>
        IO.raiseError(new RuntimeException(s"Failed to load app configuration: ${e.getMessage}", e))
      })

      _ <- Resource.eval(Logger[IO].info(s"Loaded configuration: $appConfig"))

      host <- Resource.eval(
        IO.fromOption(Host.fromString(appConfig.localConfig.serverConfig.host))(
          new RuntimeException("Invalid host in configuration")
        )
      )

      port <- Resource.eval(
        IO.fromOption(Port.fromInt(appConfig.localConfig.serverConfig.port))(
          new RuntimeException("Invalid port in configuration")
        )
      )

      transactor <- transactorResource[IO](appConfig)
      httpRoutes <- createHttpRoutes[IO](transactor, client, algorithm)
      _ <- createServer[IO](host, port, httpRoutes)
    } yield ()

    // Actual execution (run forever)
    serverResource.use(_ => IO.never).as(ExitCode.Success)
  }

}
