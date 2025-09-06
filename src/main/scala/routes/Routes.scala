package routes

import infrastructure.cache.RedisCacheImpl
import infrastructure.cache.SessionCache
import infrastructure.cache.SessionCacheImpl
import cats.effect.*
import cats.NonEmptyParallel
import configuration.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import java.net.URI
import org.http4s.client.Client
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.kafka.producers.QuestEstimationEventProducerAlgebra
import services.kafka.producers.QuestEventProducerAlgebra // <-- add this import
import services.s3.LiveS3Client
import services.s3.LiveS3Presigner
import services.s3.UploadServiceImpl
import services.LevelService
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration

object Routes {

  def baseRoutes[F[_] : Concurrent : Logger](): HttpRoutes[F] = {

    val baseController = BaseController()

    baseController.routes
  }

  def devBidRoutes[F[_] : Async : Logger : NonEmptyParallel](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val sessionCache = SessionCache(redisHost, redisPort, appConfig)
    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val devBidRepository = DevBidRepository(transactor)
    val devBidService = DevBidService(appConfig, userDataRepository, devBidRepository)
    val devBidController = DevBidController(sessionCache, devBidService)

    devBidController.routes
  }

  def questsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    questEventProducer: QuestEventProducerAlgebra[F] // <-- NEW PARAM
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val questRepository = QuestRepository(transactor)
    val hoursWorkedRepository = HoursWorkedRepository(transactor)
    val userDataRepository = UserDataRepository(transactor)
    val skillDataRepository = DevSkillRepository(transactor)
    val languageRepository = DevLanguageRepository(transactor)
    val rewardRepository = RewardRepository(transactor)

    val levelService = LevelService(skillDataRepository, languageRepository)

    val questCRUDService =
      QuestCRUDService(
        appConfig,
        questRepository,
        userDataRepository,
        hoursWorkedRepository,
        levelService,
        questEventProducer
      )

    val questStreamingService =
      QuestStreamingService(
        appConfig,
        questRepository,
        rewardRepository
      )

    val questController = QuestController(questCRUDService, questStreamingService, sessionCache)

    questController.routes
  }

  def estimateRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger : Clock](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    questEstimationEventProducer: QuestEstimationEventProducerAlgebra[F] // <-- NEW PARAM
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val questRepository = QuestRepository(transactor)
    val estimateRepository = EstimateRepository(transactor)
    val estimationExpirationRepository = EstimationExpirationRepository(transactor)
    val skillDataRepository = DevSkillRepository(transactor)
    val languageRepository = DevLanguageRepository(transactor)

    val levelService = LevelService(skillDataRepository, languageRepository)
    val estimateService = EstimateService(appConfig, userDataRepository, estimateRepository, estimationExpirationRepository, questRepository, levelService, questEstimationEventProducer)
    val estimateController = EstimateController(estimateService, sessionCache)

    estimateController.routes
  }

  def estimationExpirationRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger : Clock](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)

    val questRepository = QuestRepository(transactor)
    val estimationExpirationRepository = EstimationExpirationRepository(transactor)
    val estimationExpirationService = EstimationExpirationService(appConfig, questRepository, estimationExpirationRepository)
    val estimationExpirationController = EstimationExpirationController(sessionCache, estimationExpirationService)

    estimationExpirationController.routes
  }

  def paymentRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    client: Client[F]
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)

    val questRepository = QuestRepository(transactor)
    val rewardRepository = RewardRepository(transactor)
    val stripePaymentService = StripePaymentService(appConfig, client)
    val paymentService = LivePaymentService(stripePaymentService, questRepository, rewardRepository)
    val paymentController = new PaymentControllerImpl(paymentService, sessionCache)

    paymentController.routes
  }

  def rewardRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val rewardRepository = RewardRepository(transactor)
    val rewardService = RewardService(rewardRepository)
    val rewardController = new RewardControllerImpl(rewardService, sessionCache)

    rewardController.routes
  }
}
