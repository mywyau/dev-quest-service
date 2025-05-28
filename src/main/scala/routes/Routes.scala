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
}
