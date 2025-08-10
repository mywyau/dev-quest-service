package routes

import cache.PricingPlanCacheImpl
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
import services.stripe.StripeBillingConfig
import services.stripe.StripeBillingImpl

object RegistrationRoutes {

  def registrationRoutes[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): HttpRoutes[F] = {

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)

    val pricingPlanCache = new PricingPlanCacheImpl(redisHost, redisPort, appConfig)
    val pricingPlanRepository = PricingPlanRepository(transactor)
    val userPricingPlanRepository = UserPricingPlanRepository(transactor)
    val stripeBillingService = new StripeBillingImpl(
      StripeBillingConfig(
        apiKey = "",
        webhookSecret = ""
      )
    )
    
    val userPricingPlanService = UserPricingPlanService(appConfig, pricingPlanCache, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
    val registrationService = new RegistrationServiceImpl(userDataRepository, userPricingPlanService)
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
