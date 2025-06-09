package routes

import cache.RedisCacheImpl
import cache.SessionCache
import cache.SessionCacheImpl
import cats.effect.*
import cats.NonEmptyParallel
import configuration.models.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import repositories.*
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import services.s3.{LiveS3Client, LiveS3Presigner, UploadServiceImpl}
import services.*

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

    val questService = QuestService(questRepository)
    val questController = QuestController(questService, sessionCache)

    questController.routes
  }

  def uploadRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    // Create the AWS SDK clients
    val s3Client = S3AsyncClient
      .builder()
      .region(Region.of(appConfig.localConfig.awsS3Config.awsRegion))
      .credentialsProvider(DefaultCredentialsProvider.create())
      .build()

    val presigner = S3Presigner
      .builder()
      .region(Region.of(appConfig.localConfig.awsS3Config.awsRegion))
      .credentialsProvider(DefaultCredentialsProvider.create())
      .build()

    // Inject them into your algebras
    val liveS3Client = new LiveS3Client[F](s3Client)
    val liveS3Presigner = new LiveS3Presigner[F](presigner)

    // Make sure the real bucket name is passed from config
    val uploadService = new UploadServiceImpl[F](appConfig.localConfig.awsS3Config.uploadsBucketName, liveS3Client, liveS3Presigner)

    val uploadController = new UploadController[F](uploadService)

    uploadController.routes
  }
}
