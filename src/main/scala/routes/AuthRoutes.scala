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

object AuthRoutes {

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
}