package routes

import cats.NonEmptyParallel
import cats.effect.*
import configuration.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import infrastructure.cache.PricingPlanCacheImpl
import infrastructure.cache.RedisCacheImpl
import infrastructure.cache.SessionCache
import infrastructure.cache.SessionCacheImpl
import models.auth.UserSession
import models.pricing.PlanFeatures
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.stripe.StripeBillingServiceImpl

import java.net.URI
import java.time.Instant

object PricingPlanRoutes {

  def pricingPlanRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    appConfig: AppConfig,
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F]
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val pricingPlanCache = new PricingPlanCacheImpl(redisHost, redisPort, appConfig)
    val pricingPlanRepository = PricingPlanRepository(transactor)
    val userPricingPlanRepository = UserPricingPlanRepository(transactor)
    val stripeBillingService = new StripeBillingServiceImpl(appConfig)
    val userPricingPlanService = UserPricingPlanService(appConfig, pricingPlanCache, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
    val pricingPlanController = PricingPlanController(appConfig, sessionCache, userPricingPlanService, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)

    pricingPlanController.routes
  }

  def stripeBillingWebhookRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](
    appConfig: AppConfig,
    redisHost: String,
    redisPort: Int,
    transactor: HikariTransactor[F]
  ): HttpRoutes[F] = {

    val sessionCache = new SessionCacheImpl(redisHost, redisPort, appConfig)
    val pricingPlanCache = new PricingPlanCacheImpl(redisHost, redisPort, appConfig)
    val pricingPlanRepository = PricingPlanRepository(transactor)
    val userPricingPlanRepository = UserPricingPlanRepository(transactor)
    val stripeBillingService = new StripeBillingServiceImpl(appConfig)
    val userPricingPlanService = UserPricingPlanService(appConfig, pricingPlanCache, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
    val stripeBillingWebhookController = StripeBillingWebhookControllerImpl(stripeBillingService, userPricingPlanService)

    stripeBillingWebhookController.routes
  }

}
