package controllers.test_routes

import infrastructure.cache.*
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.*
import cats.implicits.*
import configuration.AppConfig
import configuration.BaseAppConfig
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import models.auth.UserSession
import models.cache.CacheErrors
import models.cache.CacheSuccess
import models.cache.CacheUpdateSuccess
import models.pricing.Active
import models.pricing.PlanFeatures
import models.pricing.PlanSnapshot
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.server.Router
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.*
import services.*
import services.s3.LiveS3Client
import services.s3.S3ClientAlgebra
import services.s3.S3PresignerAlgebra
import services.s3.UploadServiceImpl
import services.stripe.*
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner

import java.net.URI
import java.time.Duration
import java.time.Instant
import controllers.UploadController
import controllers.mocks.MockSessionCache

object UploadRoutes extends BaseAppConfig {

  val region = Region.US_EAST_1
  val bucket = "test-bucket"

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def s3ClientsResource(): Resource[IO, (S3AsyncClient, S3Presigner)] = {
    for {
      appConfig <- appConfigResource
      useHttps = appConfig.featureSwitches.useHttpsLocalstack
      endpoint = if (useHttps) "http://localstack:4566" else "http://localhost:4566"
      uri = URI.create(endpoint)
      s3Client <- Resource.fromAutoCloseable(IO.blocking {
        S3AsyncClient.builder()
          .endpointOverride(uri)
          .credentialsProvider(
            StaticCredentialsProvider.create(
              software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
            )
          )
          .region(Region.US_EAST_1)
          .forcePathStyle(true)
          .build()
      })

      presigner <- Resource.fromAutoCloseable(IO.blocking {
        S3Presigner.builder()
          .endpointOverride(uri)
          .credentialsProvider(
            StaticCredentialsProvider.create(
              software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
            )
          )
          .region(Region.US_EAST_1)
          .build()
      })
    } yield (s3Client, presigner)
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
      s3ResourceResult <- s3ClientsResource()
      (s3Client, presigner) = s3ResourceResult
      mockSessionCache = new MockSessionCache(ref)

      liveS3Client = new LiveS3Client[IO](s3Client)
      uploadServiceImpl = new UploadServiceImpl(
        bucket = bucket,
        client = 
          new S3ClientAlgebra[IO] {
              def putObject(bucket: String, key: String, contentType: String, bytes: Array[Byte]): IO[Unit] = 
                IO.fromCompletableFuture(IO {
                val request = PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(key)
                  .build()
                s3Client.putObject(request, software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(bytes))
              }).void
        },
        presigner = new S3PresignerAlgebra[IO] {
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
}
