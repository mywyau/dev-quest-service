import cats.effect.*
import cats.implicits.*
import cats.NonEmptyParallel
import com.comcast.ip4s.*
import configuration.models.AppConfig
import configuration.ConfigReader
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import middleware.Middleware.throttleMiddleware
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Origin
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.Router
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Uri
import org.typelevel.ci.CIString
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import routes.Routes.*
import scala.concurrent.duration.DurationInt

object Main extends IOApp {

  implicit def logger[F[_] : Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def redisAddress[F[_] : Async](appConfig: AppConfig): Resource[F, (String, Int)] = {
    val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.localConfig.redisConfig.host)
    val redisPort = sys.env.get("REDIS_PORT").flatMap(_.toIntOption).getOrElse(appConfig.localConfig.redisConfig.port)

    Resource.eval(Async[F].pure((redisHost, redisPort)))
  }

  def transactorResource[F[_] : Async](appConfig: AppConfig): Resource[F, HikariTransactor[F]] = {

    val dbHost = sys.env.getOrElse("DB_HOST", appConfig.localConfig.postgresqlConfig.host)
    val dbUser = sys.env.getOrElse("DB_USER", appConfig.localConfig.postgresqlConfig.username)
    val dbPassword = sys.env.getOrElse("DB_PASSWORD", appConfig.localConfig.postgresqlConfig.password)
    val dbName = sys.env.getOrElse("DB_NAME", appConfig.localConfig.postgresqlConfig.dbName)
    val dbPort = sys.env.getOrElse("DB_PORT", appConfig.localConfig.postgresqlConfig.port.toString)

    val dbUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"
    val driverClassName = "org.postgresql.Driver"

    for {
      ce <- ExecutionContexts.fixedThreadPool(32)
      xa <- HikariTransactor.newHikariTransactor[F](
        driverClassName,
        dbUrl,
        dbUser,
        dbPassword,
        ce
      )
    } yield xa
  }

  def createHttpRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    client: Client[F],
    appConfig: AppConfig
  ): Resource[F, HttpRoutes[F]] = {

    def corsPolicy(combinedRoutes: HttpRoutes[F]) =
      CORS.policy
        .withAllowOriginHost(
          Set(
            Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(3000)),
            Origin.Host(Uri.Scheme.https, Uri.RegName("devirl.com"), None)
          )
        )
        .withAllowCredentials(true)
        .withAllowHeadersIn(
          Set(
            CIString("Content-Type"),
            CIString("Authorization"),
            CIString("X-Requested-With"),
            CIString("Accept"),
            CIString("Origin"),
            CIString("Referer"),
            CIString("Access-Control-Request-Method"),
            CIString("Access-Control-Request-Headers")
          )
        )
        .withMaxAge(1.day)
        .withAllowMethodsIn(Set(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS))
        .apply(combinedRoutes)

    for {
      baseRoutes <- Resource.pure(baseRoutes())
      authRoutes <- Resource.pure(authRoutes(redisHost, redisPort, transactor, appConfig))
      questsRoutes <- Resource.pure(questsRoutes(redisHost, redisPort, transactor, appConfig))
      estimateRoutes <- Resource.pure(estimateRoutes(redisHost, redisPort, transactor, appConfig))
      skillRoutes <- Resource.pure(skillRoutes(transactor, appConfig))
      languageRoutes <- Resource.pure(languageRoutes(transactor, appConfig))
      paymentRoutes <- Resource.pure(paymentRoutes(redisHost, redisPort, transactor, appConfig, client))
      profileRoutes <- Resource.pure(profileRoutes(transactor, appConfig, client))
      userDataRoutes <- Resource.pure(userDataRoutes(redisHost, redisPort, transactor, appConfig))
      registrationRoutes <- Resource.pure(registrationRoutes(redisHost, redisPort, transactor, appConfig))
      uploadRoutes <- Resource.pure(uploadRoutes(transactor, appConfig))

      combinedRoutes: HttpRoutes[F] = Router(
        "/dev-quest-service" -> (
          baseRoutes <+>
            authRoutes <+>
            questsRoutes <+>
            estimateRoutes <+>
            skillRoutes <+>
            languageRoutes <+>
            registrationRoutes <+>
            userDataRoutes <+>
            uploadRoutes <+>
            profileRoutes <+>
            paymentRoutes
        )
      )
      corsRoutes = corsPolicy(combinedRoutes)
      routesToUse =
        if (appConfig.featureSwitches.useCors) {
          corsRoutes
        } else {
          combinedRoutes
        }
      throttledRoutes <- Resource.eval(throttleMiddleware(routesToUse))
    } yield throttledRoutes
  }

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

      redisAddress <- redisAddress[IO](appConfig)

      httpRoutes <- createHttpRoutes[IO](
        redisAddress._1,
        redisAddress._2,
        transactor,
        client,
        appConfig
      )
      _ <- createServer[IO](host, port, httpRoutes)
    } yield ()

    // Actual execution (run forever)
    serverResource.use(_ => IO.never).as(ExitCode.Success)
  }

}
