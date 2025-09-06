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
import infrastructure.Database
import infrastructure.KafkaProducerProvider
import infrastructure.Redis
import infrastructure.Server
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import jobs.EstimateServiceBuilder
import middleware.Middleware.throttleMiddleware
import modules.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Origin
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.Router
import org.http4s.server.Server
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


object Main extends IOApp {

  implicit def logger[F[_] : Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  override def run(args: List[String]): IO[ExitCode] = {

    val serverResource: Resource[IO, Server] = for {
      config <- Resource.eval(ConfigReader[IO].loadAppConfig)
      transactor <- DatabaseModule.make[IO](config)
      redis <- RedisModule.make[IO](config)
      kafkaProducers <- KafkaModule.make[IO](config)
      httpClient <- HttpClientModule.make[IO]
      httpApp <- HttpModule.make(config, transactor, redis, httpClient, kafkaProducers)
      _ <- BackgroundJobsModule.runAll(transactor, config, kafkaProducers.questEstimationProducer)
      host <- Resource.eval(IO.fromOption(Host.fromString(config.serverConfig.host))(new RuntimeException("Invalid host in configuration")))
      port <- Resource.eval(IO.fromOption(Port.fromInt(config.serverConfig.port))(new RuntimeException("Invalid port in configuration")))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withHttpApp(httpApp)
        .build
    } yield server

    // Run the server forever
    serverResource.use(_ => IO.never).as(ExitCode.Success)
  }
}


// Old below

// object Main extends IOApp {

//   implicit def logger[F[_] : Sync]: Logger[F] = Slf4jLogger.getLogger[F]

//   def swallow(task: IO[Unit])(implicit L: Logger[IO]): IO[Unit] =
//     task.handleErrorWith(e => L.warn(e)("[Main][swallow] finalizeExpiredEstimations failed; skipping").void)

//   def scheduleAt6HourBuckets[F[_] : Temporal](task: F[Unit]): Stream[F, Unit] = {

//     def computeNextDelay: F[FiniteDuration] = Temporal[F].realTimeInstant.map { now =>
//       val zone = ZoneId.systemDefault()
//       val localNow: LocalDateTime = now.atZone(zone).toLocalDateTime

//       // Define the 6-hour bucket times
//       val buckets = List(0, 6, 12, 18).map(h => localNow.toLocalDate.atTime(LocalTime.of(h, 0)))
//       val nextBucket = buckets.find(localNow.isBefore).getOrElse(localNow.toLocalDate.plusDays(1).atTime(0, 0))

//       val delay = java.time.Duration.between(localNow, nextBucket)
//       FiniteDuration(delay.toMillis, MILLISECONDS)
//     }

//     // Start at next 6h bucket, then every 6 hours
//     Stream.eval(computeNextDelay).flatMap { initialDelay =>
//       Stream.sleep_[F](initialDelay) ++
//         Stream.eval(task) ++
//         Stream.fixedRateStartImmediately[F](6.hours).evalMap(_ => task)
//     }
//   }

//   def estimationSchedule(appConfig: AppConfig, task: IO[Unit]): Stream[IO, Unit] =
//     if (appConfig.featureSwitches.localTesting) {
//       Stream.fixedRateStartImmediately[IO](appConfig.estimationConfig.intervalSeconds.seconds).evalMap(_ => task)
//     } else {
//       scheduleAt6HourBuckets(task)
//     }

//   def createHttpRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async](
//     redisHost: String,
//     redisPort: Int,
//     transactor: HikariTransactor[F],
//     client: Client[F],
//     appConfig: AppConfig
//   ): Resource[F, HttpRoutes[F]] = {

//     def corsPolicy(combinedRoutes: HttpRoutes[F]) =
//       CORS.policy
//         .withAllowOriginHost(
//           Set(
//             Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(3000)),
//             Origin.Host(Uri.Scheme.https, Uri.RegName("devirl.com"), None)
//           )
//         )
//         .withAllowCredentials(true)
//         .withAllowHeadersIn(
//           Set(
//             CIString("Content-Type"),
//             CIString("Authorization"),
//             CIString("X-Requested-With"),
//             CIString("Accept"),
//             CIString("Origin"),
//             CIString("Referer"),
//             CIString("Access-Control-Request-Method"),
//             CIString("Access-Control-Request-Headers")
//           )
//         )
//         .withMaxAge(1.day)
//         .withAllowMethodsIn(Set(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS))
//         .apply(combinedRoutes)

//     for {

//       kafkaProducer <- KafkaProducerProvider.make[F](
//         appConfig.kafka.bootstrapServers,
//         appConfig.kafka.clientId,
//         appConfig.kafka.acks,
//         appConfig.kafka.lingerMs,
//         appConfig.kafka.retries
//       )
//       consumerStream <- QuestCreatedConsumer
//         .resource[F](QuestCreatedConsumer.Settings(bootstrapServers = appConfig.kafka.bootstrapServers))
//       // start it in the background; the fiber will be auto-cancelled when Resource closes
//       // .evalTap(_.compile.drain.start)
//       _ <- Resource
//         .make(
//           Concurrent[F].start(consumerStream.compile.drain) // start fiber
//         )(_.cancel) // cancel on release
//         .void

//       // ── NEW: Quest event producer algebra
//       questEventProducer = new QuestEventProducerImpl[F](appConfig.kafka.topic.questCreated, kafkaProducer)
//       questEstimationEventProducer = new QuestEstimationEventProducerImpl[F](appConfig.kafka.topic.questCreated, kafkaProducer)
//       baseRoutes <- Resource.pure(baseRoutes())
//       authRoutes <- Resource.pure(authRoutes(redisHost, redisPort, transactor, appConfig))
//       devBidRoutes <- Resource.pure(devBidRoutes(redisHost, redisPort, transactor, appConfig))
//       questsRoutes <- Resource.pure(questsRoutes(redisHost, redisPort, transactor, appConfig, questEventProducer))
//       estimateRoutes <- Resource.pure(estimateRoutes(redisHost, redisPort, transactor, appConfig, questEstimationEventProducer))
//       estimationExpirationRoutes <- Resource.pure(estimationExpirationRoutes(redisHost, redisPort, transactor, appConfig))
//       hiscoreRoutes <- Resource.pure(hiscoreRoutes(transactor, appConfig))
//       skillRoutes <- Resource.pure(skillRoutes(transactor, appConfig))
//       languageRoutes <- Resource.pure(languageRoutes(transactor, appConfig))
//       paymentRoutes <- Resource.pure(paymentRoutes(redisHost, redisPort, transactor, appConfig, client))
//       pricingPlanRoutes <- Resource.pure(pricingPlanRoutes(appConfig, redisHost, redisPort, transactor))
//       stripeBillingWebhookRoutes <- Resource.pure(stripeBillingWebhookRoutes(appConfig, redisHost, redisPort, transactor))
//       profileRoutes <- Resource.pure(profileRoutes(transactor, appConfig, client))
//       rewardRoutes <- Resource.pure(rewardRoutes(redisHost, redisPort, transactor, appConfig))
//       registrationRoutes <- Resource.pure(registrationRoutes(redisHost, redisPort, transactor, appConfig))
//       userDataRoutes <- Resource.pure(userDataRoutes(redisHost, redisPort, transactor, appConfig))
//       uploadRoutes <- Resource.pure(uploadRoutes(transactor, appConfig))

//       combinedRoutes: HttpRoutes[F] = Router(
//         "/dev-quest-service" -> (
//           baseRoutes <+>
//             authRoutes <+>
//             devBidRoutes <+>
//             estimateRoutes <+>
//             estimationExpirationRoutes <+>
//             hiscoreRoutes <+>
//             languageRoutes <+>
//             profileRoutes <+>
//             paymentRoutes <+>
//             pricingPlanRoutes <+>
//             questsRoutes <+>
//             registrationRoutes <+>
//             rewardRoutes <+>
//             skillRoutes <+>
//             stripeBillingWebhookRoutes <+>
//             uploadRoutes <+>
//             userDataRoutes
//         )
//       )
//       corsRoutes = corsPolicy(combinedRoutes)
//       routesToUse =
//         if (appConfig.featureSwitches.useCors) {
//           corsRoutes
//         } else {
//           combinedRoutes
//         }
//       throttledRoutes <- Resource.eval(throttleMiddleware(routesToUse))
//     } yield throttledRoutes
//   }

//   override def run(args: List[String]): IO[ExitCode] = {
//     val configReader = ConfigReader[IO]

//     val serverAndFinalizer =
//       for {
//         client <- EmberClientBuilder.default[IO].build

//         appConfig <- Resource.eval(configReader.loadAppConfig.handleErrorWith { e =>
//           IO.raiseError(new RuntimeException(s"Failed to load app configuration: ${e.getMessage}", e))
//         })

//         _ <- Resource.eval(Logger[IO].debug(s"Loaded configuration: $appConfig"))

//         host <- Resource.eval(
//           IO.fromOption(Host.fromString(appConfig.serverConfig.host))(
//             new RuntimeException("Invalid host in configuration")
//           )
//         )

//         port <- Resource.eval(
//           IO.fromOption(Port.fromInt(appConfig.serverConfig.port))(
//             new RuntimeException("Invalid port in configuration")
//           )
//         )

//         transactor <- Database.transactor[IO](appConfig)
//         redisAddress <- Redis.address[IO](appConfig)

//         kafkaProducer <- KafkaProducerProvider.make[IO](
//           appConfig.kafka.bootstrapServers,
//           appConfig.kafka.clientId,
//           appConfig.kafka.acks,
//           appConfig.kafka.lingerMs,
//           appConfig.kafka.retries
//         )

//         questEstimationEventProducer = new QuestEstimationEventProducerImpl[IO](appConfig.kafka.topic.questCreated, kafkaProducer)
//         // Background stream: estimation finalizer every 5mins
//         estimateService = EstimateServiceBuilder.build(transactor, appConfig, questEstimationEventProducer)

//         // estimationTaskSafe = swallow(estimateService.finalizeExpiredEstimations().void)
//         // estimationFinalizer = Stream.eval(estimateService.finalizeExpiredEstimations().void) ++ estimationSchedule(appConfig, estimateService.finalizeExpiredEstimations())

//         estimationTask = estimateService.finalizeExpiredEstimations().void

//         estimationTaskSafe = swallow(estimationTask)

//         estimationFinalizer =
//           (Stream.eval(estimationTaskSafe) ++ // initial run (swallows errors)
//             estimationSchedule(appConfig, estimationTaskSafe)) // scheduled runs (swallow errors)
//             .handleErrorWith(e => // if the *stream* itself crashes, stop quietly
//               Stream.eval(Logger[IO].warn(e)("finalizer stream crashed; stopping")) >> Stream.empty
//             )

//         httpRoutes <- createHttpRoutes[IO](
//           redisAddress._1,
//           redisAddress._2,
//           transactor,
//           client,
//           appConfig
//         )
//         _ <- Server.create[IO](host, port, httpRoutes)
//       }
//       // yield ()
//       yield estimationFinalizer

//     serverAndFinalizer.use(_.compile.drain).as(ExitCode.Success)
//     // serverAndFinalizer.use(_ => IO.never).as(ExitCode.Success)
//   }

// }