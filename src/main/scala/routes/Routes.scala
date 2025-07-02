package routes

import cache.RedisCacheImpl
import cache.SessionCache
import cache.SessionCacheImpl
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

  def authRoutes[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val sessionCache = SessionCache(redisHost, redisPort, appConfig)
    val sessionService = SessionService(userDataRepository, sessionCache)
    val authController = AuthController(sessionService)

    authController.routes
  }

  def userDataRoutes[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val userDataService = new UserDataServiceImpl(userDataRepository)

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val sessionService = SessionService(userDataRepository, sessionCache)

    val userDataController = UserDataController(userDataService, sessionCache)

    userDataController.routes
  }

  def registrationRoutes[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val registrationService = new RegistrationServiceImpl(userDataRepository)
    val registrationController = RegistrationController(registrationService, sessionCache)

    registrationController.routes
  }

  def questsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val questRepository = QuestRepository(transactor)
    val userDataRepository = UserDataRepository(transactor)
    val skillDataRepository = SkillDataRepository(transactor)
    val langaugeRepository = LanguageRepository(transactor)
    val rewardRepository = RewardRepository(transactor)

    val levelService = LevelService(skillDataRepository, langaugeRepository)

    val questCRUDService =
      QuestCRUDService(
        appConfig,
        questRepository,
        userDataRepository,
        levelService
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

  def estimateRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val estimateRepository = EstimateRepository(transactor)
    val estimateService = EstimateService(userDataRepository, estimateRepository)
    val estimateController = EstimateController(estimateService, sessionCache)

    estimateController.routes
  }

  def skillRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val levelService = LevelService
    val skillRepository = SkillDataRepository(transactor)
    val skillService = SkillDataService(skillRepository)
    val skillController = SkillController(skillService)

    skillController.routes
  }

  def languageRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val levelService = LevelService
    val languageRepository = LanguageRepository(transactor)
    val languageService = LanguageService(languageRepository)
    val languageController = LanguageController(languageService)

    languageController.routes
  }

  def profileRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    client: Client[F]
  ): HttpRoutes[F] = {

    val levelService = LevelService
    val stripeAccountRepository = StripeAccountRepository(transactor)
    val skillRepository = SkillDataRepository(transactor)
    val languageRepository = LanguageRepository(transactor)

    val stripePaymentService = StripeRegistrationService(stripeAccountRepository, appConfig, client)
    val profileService = ProfileService(skillRepository, languageRepository)

    val profileController = ProfileController(profileService, stripePaymentService)

    profileController.routes
  }

  def paymentRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    client: Client[F] // <-- accept HTTP client here
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

  def uploadRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    // Create the AWS SDK clients

    val s3Client =
      if (appConfig.featureSwitches.localTesting) {
        val s3Config =
          S3Configuration
            .builder()
            .pathStyleAccessEnabled(true)
            .build()

        S3AsyncClient
          .builder()
          .region(Region.of(appConfig.localAppConfig.awsS3Config.awsRegion))
          .endpointOverride(URI.create("http://localhost:4566"))
          .serviceConfiguration(s3Config)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      } else {
        S3AsyncClient
          .builder()
          .region(Region.of(appConfig.localAppConfig.awsS3Config.awsRegion))
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      }

    val presigner =
      if (appConfig.featureSwitches.localTesting) {
        val s3Config =
          S3Configuration
            .builder()
            .pathStyleAccessEnabled(true)
            .build()

        S3Presigner
          .builder()
          .region(Region.of(appConfig.localAppConfig.awsS3Config.awsRegion))
          .endpointOverride(URI.create("http://localhost:4566"))
          .serviceConfiguration(s3Config)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      } else {
        S3Presigner
          .builder()
          .region(Region.of(appConfig.localAppConfig.awsS3Config.awsRegion))
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      }

    // Inject them into your algebras
    val liveS3Client = new LiveS3Client[F](s3Client)
    val liveS3Presigner = new LiveS3Presigner[F](presigner)

    // Make sure the real bucket name is passed from config
    val uploadService = new UploadServiceImpl[F](appConfig.localAppConfig.awsS3Config.bucketName, liveS3Client, liveS3Presigner)

    val devSubmissionRepo = DevSubmissionRepository[F](transactor, appConfig)
    val devSubmissionService = DevSubmissionService[F](devSubmissionRepo)

    val uploadController = new UploadController[F](uploadService, devSubmissionService, appConfig)

    uploadController.routes
  }
}
