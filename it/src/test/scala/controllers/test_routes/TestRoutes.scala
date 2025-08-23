package controllers.test_routes


import infrastructure.cache.*
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.*
import cats.implicits.*
import infrastructure.KafkaProducerProvider
import configuration.AppConfig
import configuration.BaseAppConfig
import controllers.mocks.*
import controllers.test_routes.AuthRoutes.*
import controllers.test_routes.QuestRoutes.questRoutes
import controllers.test_routes.UploadRoutes.*
import controllers.BaseController
import controllers.PricingPlanController
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import fs2.kafka.*
import java.net.URI
import java.time.Duration
import java.time.Instant
import models.auth.UserSession
import models.cache.CacheErrors
import models.cache.CacheSuccess
import models.cache.CacheUpdateSuccess
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
import services.kafka.consumers.QuestCreatedConsumer
import services.kafka.producers.QuestEventProducerImpl
import services.s3.LiveS3Client
import services.s3.S3ClientAlgebra
import services.s3.S3PresignerAlgebra
import services.s3.UploadServiceImpl
import services.stripe.*

object TestRoutes extends BaseAppConfig {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def baseRoutes(): HttpRoutes[IO] = {
    val baseController = BaseController[IO]()
    baseController.routes
  }

  def pricingPlanRoutes(transactor: Transactor[IO], appConfig: AppConfig, redisHost: String, redisPort: Int): Resource[IO, HttpRoutes[IO]] = {

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

      // For integration tests, stub Stripe, don't use the real impl
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
      authSessionRef <- Resource.eval(
        Ref.of[IO, Map[String, UserSession]](
          Map(
            s"auth:session:USER001" -> fakeUserSession("USER001"),
            s"auth:session:USER002" -> fakeUserSession("USER002"),
            s"auth:session:USER003" -> fakeUserSession("USER003"),
            s"auth:session:USER004" -> fakeUserSession("USER004"),
            s"auth:session:USER005" -> fakeUserSession("USER005"),
            s"auth:session:USER006" -> fakeUserSession("USER006"),
            s"auth:session:USER007" -> fakeUserSession("USER007")
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
      mockSessionCache = new MockSessionCache(authSessionRef)
      mockPricingPlanCache = new MockPricingPlanCache(pricingRef)
      pricingPlanRepository = PricingPlanRepository(transactor)
      userPricingPlanRepository = UserPricingPlanRepository(transactor)
      stripeBillingService = stripeStub
      userPricingPlanService = UserPricingPlanService(appConfig, mockPricingPlanCache, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
      pricingPlanController = PricingPlanController(appConfig, mockSessionCache, userPricingPlanService, pricingPlanRepository, userPricingPlanRepository, stripeBillingService)
    } yield pricingPlanController.routes
  }

  def createTestRouter(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.redisConfig.host)
    val redisPort = sys.env.get("REDIS_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(appConfig.redisConfig.port)

    for {
      kafkaProducer: KafkaProducer[IO, String, String] <- KafkaProducerProvider.make[IO](
        appConfig.kafka.bootstrapServers,
        appConfig.kafka.clientId,
        appConfig.kafka.acks,
        appConfig.kafka.lingerMs,
        appConfig.kafka.retries
      )
      consumerStream <- QuestCreatedConsumer.resource[IO](QuestCreatedConsumer.Settings(bootstrapServers = appConfig.kafka.bootstrapServers))
      _ <- Resource
        .make(Concurrent[IO].start(consumerStream.compile.drain))(_.cancel)
        .void

      questEventProducer = new QuestEventProducerImpl[IO](appConfig.kafka.topic.questCreated, kafkaProducer)
      registrationRoutes <- registrationRoutes(transactor, appConfig)
      userDataRoutes <- userDataRoutes(transactor, appConfig)
      questRoute <- questRoutes(transactor, appConfig, questEventProducer)
      uploadRoutes <- uploadRoutes(transactor, appConfig)
      pricingPlanRoutes <- pricingPlanRoutes(transactor, appConfig, redisHost, redisPort)
    } yield Router(
      "/dev-quest-service" -> (
        baseRoutes() <+>
          authRoutes(redisHost, redisPort, transactor, appConfig) <+>
          questRoute <+>
          userDataRoutes <+>
          registrationRoutes <+>
          uploadRoutes <+>
          pricingPlanRoutes
      )
    )
  }
}
