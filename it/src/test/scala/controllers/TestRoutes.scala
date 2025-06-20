package controllers

import cache.RedisCacheAlgebra
import cache.RedisCacheImpl
import cache.SessionCacheAlgebra
import cache.SessionCacheImpl
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.*
import cats.implicits.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import models.auth.UserSession
import models.cache.CacheErrors
import models.cache.CacheSuccess
import models.cache.CacheUpdateSuccess
import org.http4s.server.Router
import org.http4s.HttpRoutes
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repositories.*
import services.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.net.URI
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import org.http4s.Uri
import java.time.Duration
import services.s3.LiveS3Client
import services.s3.UploadServiceImpl
import services.s3.S3PresignerAlgebra
import services.s3.S3ClientAlgebra

object TestRoutes {

  val region = Region.US_EAST_1
  val bucket = "test-bucket"
  val endpoint =  "http://localstack:4566"
  // val endpoint =  "http://localhost:4566"

  val s3Client: S3AsyncClient = S3AsyncClient.builder()
    .endpointOverride(URI.create(endpoint))
    .credentialsProvider(
      StaticCredentialsProvider.create(
        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
      )
    )
    .region(region)
    .forcePathStyle(true) // ✅ This fixes the DNS issue
    .build()

  val presigner: S3Presigner = S3Presigner.builder()
    .endpointOverride(URI.create(endpoint))
    .credentialsProvider(
      StaticCredentialsProvider.create(
        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
      )
    )
    .region(region)
    // .forcePathStyle(true) // ✅ This fixes the DNS issue
    .build()

  class MockRedisCache(ref: Ref[IO, Map[String, UserSession]]) extends RedisCacheAlgebra[IO] {

    override def updateSession(userId: String, token: String): IO[Unit] =
      ref.update(
        _.updated(
          s"auth:session:$userId",
          UserSession(
            userId = userId,
            cookieValue = token,
            email = s"$userId@example.com",
            userType = "Dev"
          )
        )
      )

    override def deleteSession(userId: String): IO[Long] = ???

    def storeSession(userId: String, token: String): IO[Unit] =
      ref.update(
        _.updated(
          s"auth:session:$userId",
          UserSession(
            userId = userId,
            cookieValue = token,
            email = s"$userId@example.com",
            userType = "Dev"
          )
        )
      )

    def getSession(userId: String): IO[Option[UserSession]] =
      ref.get.map(_.get(s"auth:session:$userId"))
  }

  class MockSessionCache(ref: Ref[IO, Map[String, UserSession]]) extends SessionCacheAlgebra[IO] {

    override def getSessionCookieOnly(userId: String): IO[Option[String]] = IO(Some("test-session-token"))

    override def lookupSession(token: String): IO[Option[UserSession]] = ???

    override def storeOnlyCookie(userId: String, token: String): IO[Unit] = ???

    override def storeSession(userId: String, session: Option[UserSession]): IO[ValidatedNel[CacheErrors, CacheSuccess]] = ???

    override def getSession(userId: String): IO[Option[UserSession]] =
      ref.get.map(_.get(s"auth:session:$userId"))

    override def updateSession(userId: String, session: Option[UserSession]): IO[ValidatedNel[CacheErrors, CacheSuccess]] =
      ref
        .update(
          _.updated(
            s"auth:session:$userId",
            UserSession(
              userId = userId,
              cookieValue = session.map(_.cookieValue).getOrElse("no-cookie-available"),
              email = s"$userId@example.com",
              userType = "Dev"
            )
          )
        )
        .as(Validated.valid(CacheUpdateSuccess))

    override def deleteSession(userId: String): IO[Long] =
      ref.modify { current =>
        val removed = current - s"auth:session:$userId"
        val wasPresent = current.contains(s"auth:session:$userId")
        (removed, if (wasPresent) 1L else 0L)
      }
  }

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def baseRoutes(): HttpRoutes[IO] = {
    val baseController = BaseController[IO]()
    baseController.routes
  }

  def authRoutes(
    redisHost: String,
    redisPort: Int,
    transactor: Transactor[IO],
    appConfig: AppConfig
  ): HttpRoutes[IO] = {

    val userDataRepository = UserDataRepository(transactor)
    val sessionCache = new SessionCacheImpl[IO](redisHost, redisPort, appConfig)
    val sessionService = new SessionServiceImpl[IO](userDataRepository, sessionCache)
    val authController = AuthController(sessionService)

    authController.routes
  }

  def questRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val sessionToken = "test-session-token"

    def fakeUserSession(userId: String) =
      UserSession(
        userId = userId,
        cookieValue = sessionToken,
        email = s"$userId@example.com",
        userType = "Dev"
      )

    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, UserSession]](
          Map(
            s"auth:session:USER001" -> fakeUserSession("USER001"),
            s"auth:session:USER002" -> fakeUserSession("USER002"),
            s"auth:session:USER003" -> fakeUserSession("USER003"),
            s"auth:session:USER004" -> fakeUserSession("USER004"),
            s"auth:session:USER005" -> fakeUserSession("USER005"),
            s"auth:session:USER006" -> fakeUserSession("USER006"),
            s"auth:session:USER007" -> fakeUserSession("USER007")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      questRepository = QuestRepository(transactor)
      userDataRepository = UserDataRepository(transactor)
      skillDataRepository = SkillDataRepository(transactor)
      languageRepository = LanguageRepository(transactor)
      questService = QuestService(questRepository, userDataRepository, skillDataRepository, languageRepository)
      questController = QuestController(questService, mockSessionCache)
    } yield questController.routes
  }

  def userDataRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val sessionToken = "test-session-token"

    def fakeUserSession(userId: String) =
      UserSession(
        userId = userId,
        cookieValue = sessionToken,
        email = s"$userId@example.com",
        userType = "Dev"
      )

    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, UserSession]](
          Map(
            s"auth:session:USER001" -> fakeUserSession("USER001"),
            s"auth:session:USER002" -> fakeUserSession("USER002"),
            s"auth:session:USER003" -> fakeUserSession("USER003"),
            s"auth:session:USER004" -> fakeUserSession("USER004"),
            s"auth:session:USER005" -> fakeUserSession("USER005"),
            s"auth:session:USER006" -> fakeUserSession("USER006"),
            s"auth:session:USER008" -> fakeUserSession("USER008"),
            s"auth:session:USER009" -> fakeUserSession("USER009"),
            s"auth:session:USER010" -> fakeUserSession("USER010")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      userDataRepository = UserDataRepository(transactor)
      userDataService = UserDataService(userDataRepository)
      userDataController = UserDataController(userDataService, mockSessionCache)
    } yield userDataController.routes
  }

  def registrationRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val sessionToken = "test-session-token"

    def fakeUserSession(userId: String) =
      UserSession(
        userId = userId,
        cookieValue = sessionToken,
        email = s"$userId@example.com",
        userType = "Dev"
      )

    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, UserSession]](
          Map(
            s"auth:session:USER001" -> fakeUserSession("USER001"),
            s"auth:session:USER002" -> fakeUserSession("USER002"),
            s"auth:session:USER003" -> fakeUserSession("USER003"),
            s"auth:session:USER004" -> fakeUserSession("USER004"),
            s"auth:session:USER005" -> fakeUserSession("USER005"),
            s"auth:session:USER006" -> fakeUserSession("USER006"),
            s"auth:session:USER007" -> fakeUserSession("USER007"),
            s"auth:session:USER008" -> fakeUserSession("USER008"),
            s"auth:session:USER009" -> fakeUserSession("USER009"),
            s"auth:session:USER010" -> fakeUserSession("USER010")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      userDataRepository = UserDataRepository(transactor)
      registrationService = RegistrationService(userDataRepository)
      registrationController = RegistrationController(registrationService, mockSessionCache)
    } yield registrationController.routes
  }

  def uploadRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val sessionToken = "test-session-token"

    def fakeUserSession(userId: String) =
      UserSession(
        userId = userId,
        cookieValue = sessionToken,
        email = s"$userId@example.com",
        userType = "Dev"
      )

    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, UserSession]](
          Map(
            s"auth:session:USER001" -> fakeUserSession("USER001"),
            s"auth:session:USER002" -> fakeUserSession("USER002"),
            s"auth:session:USER003" -> fakeUserSession("USER003"),
            s"auth:session:USER004" -> fakeUserSession("USER004"),
            s"auth:session:USER005" -> fakeUserSession("USER005"),
            s"auth:session:USER006" -> fakeUserSession("USER006"),
            s"auth:session:USER007" -> fakeUserSession("USER007"),
            s"auth:session:USER008" -> fakeUserSession("USER008"),
            s"auth:session:USER009" -> fakeUserSession("USER009"),
            s"auth:session:USER010" -> fakeUserSession("USER010")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)

      liveS3Client = new LiveS3Client[IO](s3Client)
      uploadServiceImpl = new UploadServiceImpl(
        bucket,
        new S3ClientAlgebra[IO] {
              def putObject(bucket: String, key: String, contentType: String, bytes: Array[Byte]): IO[Unit] = IO.fromCompletableFuture(IO {
                val request = PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(key)
                  .build()
                s3Client.putObject(request, software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(bytes))
              }).void
        },
        new S3PresignerAlgebra[IO] {
            def presignGetUrl(bucket: String, key: String, fileName: String, expiresIn: Duration): IO[Uri] = IO {
              val req = 
                GetObjectRequest
                .builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition(s"""attachment; filename="$fileName"""")
                .build()
              val presign = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
                .builder()
                .getObjectRequest(req)
                .signatureDuration(expiresIn)
                .build()
              Uri.unsafeFromString(presigner.presignGetObject(presign).url().toString)
            }

            def presignPutUrl(bucket: String, key: String, expiresIn: Duration): IO[Uri] = IO {
              val req = PutObjectRequest.builder().bucket(bucket).key(key).build()
              val presign = software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
                .builder()
                .putObjectRequest(req)
                .signatureDuration(expiresIn)
                .build()
              Uri.unsafeFromString(presigner.presignPutObject(presign).url().toString)
            }
    }
      )
      devSubmissionRepository = DevSubmissionRepository(transactor, appConfig)
      devSubmissionService = DevSubmissionService(devSubmissionRepository)
      uploadController = UploadController(uploadServiceImpl, devSubmissionService, appConfig)
    } yield uploadController.routes
  }

  def createTestRouter(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.integrationSpecConfig._3.host)
    val redisPort = sys.env.get("REDIS_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(appConfig.integrationSpecConfig._3.port)

    for {
      registrationRoutes <- registrationRoutes(transactor, appConfig)
      userDataRoutes <- userDataRoutes(transactor, appConfig)
      questRoute <- questRoutes(transactor, appConfig)
      uploadRoutes <- uploadRoutes(transactor, appConfig)
    } yield Router(
      "/dev-quest-service" -> (
        baseRoutes() <+>
          authRoutes(redisHost, redisPort, transactor, appConfig) <+>
          questRoute <+>
          userDataRoutes <+>
          registrationRoutes <+>
          uploadRoutes
      )
    )
  }
}
