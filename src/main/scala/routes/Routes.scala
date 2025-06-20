package routes

import cache.RedisCacheImpl
import cache.SessionCache
import cache.SessionCacheImpl
import cats.NonEmptyParallel
import cats.effect.*
import configuration.models.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.s3.LiveS3Client
import services.s3.LiveS3Presigner
import services.s3.UploadServiceImpl
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

import java.net.URI

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

    val questService = QuestService(
      questRepository,
      userDataRepository,
      skillDataRepository,
      langaugeRepository
    )
    val questController = QuestController(questService, sessionCache)

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

    val skillRepository = SkillDataRepository(transactor)
    val skillService = SkillDataService(skillRepository)
    val skillController = SkillController(skillService)

    skillController.routes
  }

  def languageRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val languageRepository = LanguageRepository(transactor)
    val languageService = LanguageService(languageRepository)
    val languageController = LanguageController(languageService)

    languageController.routes
  }

  def profileRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val skillRepository = SkillDataRepository(transactor)
    val languageRepository = LanguageRepository(transactor)

    val profileService = ProfileService(skillRepository, languageRepository)

    val profileController = ProfileController(profileService)

    profileController.routes
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
          .region(Region.of(appConfig.localConfig.awsS3Config.awsRegion))
          .endpointOverride(URI.create("http://localhost:4566"))
          .serviceConfiguration(s3Config)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      } else {
        S3AsyncClient
          .builder()
          .region(Region.of(appConfig.localConfig.awsS3Config.awsRegion))
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
          .region(Region.of(appConfig.localConfig.awsS3Config.awsRegion))
          .endpointOverride(URI.create("http://localhost:4566"))
          .serviceConfiguration(s3Config)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      } else {
        S3Presigner
          .builder()
          .region(Region.of(appConfig.localConfig.awsS3Config.awsRegion))
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      }

    // Inject them into your algebras
    val liveS3Client = new LiveS3Client[F](s3Client)
    val liveS3Presigner = new LiveS3Presigner[F](presigner)

    // Make sure the real bucket name is passed from config
    val uploadService = new UploadServiceImpl[F](appConfig.localConfig.awsS3Config.bucketName, liveS3Client, liveS3Presigner)

    val devSubmissionRepo = DevSubmissionRepository[F](transactor, appConfig)
    val devSubmissionService = DevSubmissionService[F](devSubmissionRepo)

    val uploadController = new UploadController[F](uploadService, devSubmissionService, appConfig)

    uploadController.routes
  }
}
