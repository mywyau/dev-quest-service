package controllers.test_routes

import cache.RedisCacheAlgebra
import cache.RedisCacheImpl
import cache.SessionCacheAlgebra
import cache.SessionCacheImpl
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.*
import cats.implicits.*
import configuration.AppConfig
import configuration.BaseAppConfig
import controllers.mocks.*
import controllers.AuthController
import controllers.RegistrationController
import controllers.UserDataController
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import java.net.URI
import java.time.Duration
import java.time.Instant
import models.auth.UserSession
import models.cache.*
import models.pricing.Active
import models.pricing.PlanFeatures
import models.pricing.PlanSnapshot
import org.http4s.server.Router
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repositories.*
import services.*
import services.stripe.StripeBillingServiceAlgebra
import services.stripe.StripeSubState

object AuthRoutes extends BaseAppConfig {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def authRoutes(
    redisHost: String,
    redisPort: Int,
    transactor: Transactor[IO],
    appConfig: AppConfig
  ): HttpRoutes[IO] = {

    val userDataRepository = UserDataRepository(transactor)
    val sessionCache = new SessionCacheImpl[IO](redisHost, redisPort, appConfig)
    val sessionService = new SessionServiceImpl[IO](userDataRepository, sessionCache)
    val authController = AuthController(sessionService)

    authController.routes
  }

  def userDataRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

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
            s"auth:session:USER008" -> fakeUserSession("USER008"),
            s"auth:session:USER009" -> fakeUserSession("USER009"),
            s"auth:session:USER010" -> fakeUserSession("USER010")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      userDataRepository = UserDataRepository(transactor)
      userDataService = UserDataService(userDataRepository)
      userDataController = UserDataController(userDataService, mockSessionCache)
    } yield userDataController.routes
  }

  def registrationRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val sessionToken = "test-session-token"

    def fakeUserSession(userId: String) =
      UserSession(
        userId = userId,
        cookieValue = sessionToken,
        email = s"$userId@example.com",
        userType = "Dev"
      )

    val planFeatures =
      PlanFeatures(
        maxActiveQuests = Some(1),
        devPool = Some("invite"),
        estimations = Some(true),
        canCustomizeLevelThresholds = Some(true),
        boostQuests = Some(true),
        showOnLeaderBoard = Some(true),
        communicateWithClient = Some(true)
      )

    val fixedCurrentPeriodEnd: Instant = Instant.parse("2025-01-02T00:00:00Z")

    def fakePlanSnapshot(userId: String) =
      PlanSnapshot(
        userId = userId,
        planId = "PLAN123",
        status = Active,
        features = planFeatures,
        currentPeriodEnd = Some(fixedCurrentPeriodEnd),
        cancelAtPeriodEnd = false
      )

    val stripeStub = new StripeBillingServiceAlgebra[IO] {

      override def setCancelAtPeriodEnd(subscriptionId: String, cancel: Boolean): IO[StripeSubState] = ???

      def createCheckoutSession(userId: String, priceId: String, success: String, cancel: String, idem: String) =
        IO.pure("https://example/checkout")

      def createBillingPortalSession(customerId: String, returnUrl: String) =
        IO.pure("https://example/portal")

      def verifyAndParseEvent(raw: String, sig: String) =
        IO.raiseError(new Exception("not used in this test route"))
    }

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
      pricingRef <- Resource.eval(
        Ref.of[IO, Map[String, PlanSnapshot]](
          Map(
            s"user:USER001:plan" -> fakePlanSnapshot("USER001"),
            s"user:USER002:plan" -> fakePlanSnapshot("USER002"),
            s"user:USER003:plan" -> fakePlanSnapshot("USER003"),
            s"user:USER004:plan" -> fakePlanSnapshot("USER004"),
            s"user:USER005:plan" -> fakePlanSnapshot("USER005"),
            s"user:USER006:plan" -> fakePlanSnapshot("USER006"),
            s"user:USER007:plan" -> fakePlanSnapshot("USER007")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      userDataRepository = UserDataRepository(transactor)
      mockPricingPlanCache = new MockPricingPlanCache(pricingRef)
      pricingPlanRepository = PricingPlanRepository(transactor)
      userPricingPlanRepository = UserPricingPlanRepository(transactor)
      stripeBillingService = stripeStub
      userPricingPlanService = UserPricingPlanService(appConfig, mockPricingPlanCache, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
      registrationService = RegistrationService(userDataRepository, userPricingPlanService)
      registrationController = RegistrationController(registrationService, mockSessionCache)
    } yield registrationController.routes
  }

}
