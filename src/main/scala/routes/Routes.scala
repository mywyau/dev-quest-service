package routes

import cache.RedisCacheImpl
import cats.effect.*
import cats.NonEmptyParallel
import configuration.models.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.QuestService

object Routes {

  def baseRoutes[F[_] : Concurrent : Logger](): HttpRoutes[F] = {

    val baseController = BaseController()

    baseController.routes
  }

  def authRoutes[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val redisCache = new RedisCacheImpl(redisHost, redisPort, appConfig)
    val authController = AuthController(redisCache)

    authController.routes
  }

  def questsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val redisCache = new RedisCacheImpl(redisHost, redisPort, appConfig)
    val questRepository = QuestRepository(transactor)

    val questService = QuestService(questRepository)
    val questController = QuestController(questService, redisCache)

    questController.routes
  }
}
