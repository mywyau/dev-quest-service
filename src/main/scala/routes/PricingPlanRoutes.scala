package routes

import cache.PricingPlanCacheImpl
import cache.RedisCacheImpl
import cache.SessionCache
import cache.SessionCacheImpl
import cats.NonEmptyParallel
import cats.effect.*
import configuration.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import models.auth.UserSession
import models.pricing.PlanFeatures
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.stripe.StripeBillingConfig
import services.stripe.StripeBillingImpl

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
    val stripeBillingService = new StripeBillingImpl(
      StripeBillingConfig(
        apiKey = "sk_test_51RIzs009eXrPaQIgWsX2OVqqwFhHbimkRZi9uDKFmLRYFGaAsnwHX50uBjZOmy7dO5sUQsnaVkqZsPdIfILk9GNO00QORcn57s",
        webhookSecret = "whsec_b6869c242a15a958b1db2cad417d34f5a3e318193a86bab1fa3e120ffafcb3d8"
      )
    )
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
    val stripeBillingService = new StripeBillingImpl(
      StripeBillingConfig(
        apiKey = "sk_test_51RIzs009eXrPaQIgWsX2OVqqwFhHbimkRZi9uDKFmLRYFGaAsnwHX50uBjZOmy7dO5sUQsnaVkqZsPdIfILk9GNO00QORcn57s",
        webhookSecret = "whsec_b6869c242a15a958b1db2cad417d34f5a3e318193a86bab1fa3e120ffafcb3d8"
      )
    )
    val userPricingPlanService = UserPricingPlanService(appConfig, pricingPlanCache, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
    val stripeBillingWebhookController = StripeBillingWebhookControllerImpl(stripeBillingService, userPricingPlanService)

    stripeBillingWebhookController.routes
  }

}
