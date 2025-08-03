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

object HiscoreRoutes {

  def hiscoreRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val skillDataRepository = DevSkillRepository(transactor)
    val languageRepository = DevLanguageRepository(transactor)
    val levelService = LevelService(skillDataRepository, languageRepository)

    val hiscoreController = HiscoreController(levelService)

    hiscoreController.routes
  }

  def skillRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val levelService = LevelService
    val skillRepository = DevSkillRepository(transactor)
    val skillService = SkillDataService(skillRepository)
    val skillController = SkillController(skillService)

    skillController.routes
  }

  def languageRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val levelService = LevelService
    val languageRepository = DevLanguageRepository(transactor)
    val languageService = LanguageService(languageRepository)
    val languageController = LanguageController(languageService)

    languageController.routes
  }
}
