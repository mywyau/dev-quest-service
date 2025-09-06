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

object UploadRoutes {

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
          .region(Region.of(appConfig.awsS3Config.awsRegion))
          .endpointOverride(URI.create("http://localhost:4566"))
          .serviceConfiguration(s3Config)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      } else {
        S3AsyncClient
          .builder()
          .region(Region.of(appConfig.awsS3Config.awsRegion))
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
          .region(Region.of(appConfig.awsS3Config.awsRegion))
          .endpointOverride(URI.create("http://localhost:4566"))
          .serviceConfiguration(s3Config)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      } else {
        S3Presigner
          .builder()
          .region(Region.of(appConfig.awsS3Config.awsRegion))
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build()
      }

    // Inject them into your algebras
    val liveS3Client = new LiveS3Client[F](s3Client)
    val liveS3Presigner = new LiveS3Presigner[F](presigner)

    // Make sure the real bucket name is passed from config
    val uploadService = new UploadServiceImpl[F](appConfig.awsS3Config.bucketName, liveS3Client, liveS3Presigner)

    val devSubmissionRepo = DevSubmissionRepository[F](transactor, appConfig)
    val devSubmissionService = DevSubmissionService[F](devSubmissionRepo)

    val uploadController = new UploadController[F](uploadService, devSubmissionService, appConfig)

    uploadController.routes
  }
}
