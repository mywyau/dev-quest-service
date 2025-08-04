package routes

import cache.RedisCacheImpl
import cache.SessionCache
import cache.SessionCacheImpl
import cats.NonEmptyParallel
import cats.effect.*
import configuration.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import repositories.*
import services.*

import java.net.URI

object RegistrationRoutes {

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

  def profileRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    client: Client[F]
  ): HttpRoutes[F] = {

    val levelService = LevelService
    val stripeAccountRepository = StripeAccountRepository(transactor)
    val skillRepository = DevSkillRepository(transactor)
    val languageRepository = DevLanguageRepository(transactor)

    val stripePaymentService = StripeRegistrationService(stripeAccountRepository, appConfig, client)
    val profileService = ProfileService(skillRepository, languageRepository)

    val profileController = ProfileController(profileService, stripePaymentService)

    profileController.routes
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
}
