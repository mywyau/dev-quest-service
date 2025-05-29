package controllers

import cache.RedisCacheAlgebra
import cache.RedisCacheImpl
import cache.SessionCacheAlgebra
import cache.SessionCacheImpl
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.*
import cats.implicits.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import models.auth.UserSession
import models.cache.CacheErrors
import models.cache.CacheSuccess
import models.cache.CacheUpdateSuccess
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.*
import services.*

import scala.concurrent.duration.*

object TestRoutes {

  class MockRedisCache(ref: Ref[IO, Map[String, UserSession]]) extends RedisCacheAlgebra[IO] {

    override def updateSession(userId: String, token: String): IO[Unit] =
      ref.update(
        _.updated(
          s"auth:session:$userId",
          UserSession(
            userId = userId,
            cookieValue = token,
            email = s"$userId@example.com",
            userType = "Dev"
          )
        )
      )

    override def deleteSession(userId: String): IO[Long] = ???

    def storeSession(userId: String, token: String): IO[Unit] =
      ref.update(
        _.updated(
          s"auth:session:$userId",
          UserSession(
            userId = userId,
            cookieValue = token,
            email = s"$userId@example.com",
            userType = "Dev"
          )
        )
      )

    def getSession(userId: String): IO[Option[UserSession]] =
      ref.get.map(_.get(s"auth:session:$userId"))
  }

  class MockSessionCache(ref: Ref[IO, Map[String, UserSession]]) extends SessionCacheAlgebra[IO] {

    override def getSessionCookieOnly(userId: String): IO[Option[String]] = IO(Some("test-session-token"))

    override def lookupSession(token: String): IO[Option[UserSession]] = ???

    override def storeOnlyCookie(userId: String, token: String): IO[Unit] = ???

    override def storeSession(userId: String, session: Option[UserSession]): IO[ValidatedNel[CacheErrors, CacheSuccess]] = ???

    override def getSession(userId: String): IO[Option[UserSession]] =
      ref.get.map(_.get(s"auth:session:$userId"))

    override def updateSession(userId: String, session: Option[UserSession]): IO[ValidatedNel[CacheErrors, CacheSuccess]] =
      ref
        .update(
          _.updated(
            s"auth:session:$userId",
            UserSession(
              userId = userId,
              cookieValue = session.map(_.cookieValue).getOrElse("no-cookie-available"),
              email = s"$userId@example.com",
              userType = "Dev"
            )
          )
        )
        .as(Validated.valid(CacheUpdateSuccess))

    override def deleteSession(userId: String): IO[Long] =
      ref.modify { current =>
        val removed = current - s"auth:session:$userId"
        val wasPresent = current.contains(s"auth:session:$userId")
        (removed, if (wasPresent) 1L else 0L)
      }
  }

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def baseRoutes(): HttpRoutes[IO] = {
    val baseController = BaseController[IO]()
    baseController.routes
  }

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

  def questRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

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
            s"auth:session:USER006" -> fakeUserSession("USER006")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      questRepository = QuestRepository(transactor)
      questService = QuestService(questRepository)
      questController = QuestController(questService, mockSessionCache)
    } yield questController.routes
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
      mockSessionCache = new MockSessionCache(ref)
      userDataRepository = UserDataRepository(transactor)
      registrationService = RegistrationService(userDataRepository)
      registrationController = RegistrationController(registrationService, mockSessionCache)
    } yield registrationController.routes
  }

  def createTestRouter(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.integrationSpecConfig._3.host)
    val redisPort = sys.env.get("REDIS_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(appConfig.integrationSpecConfig._3.port)

    for {
      registrationRoutes <- registrationRoutes(transactor, appConfig)
      userDataRoutes <- userDataRoutes(transactor, appConfig)
      questRoute <- questRoutes(transactor, appConfig)
    } yield Router(
      "/dev-quest-service" -> (
        baseRoutes() <+>
          authRoutes(redisHost, redisPort, transactor, appConfig) <+>
          questRoute <+>
          userDataRoutes <+>
          registrationRoutes
      )
    )
  }
}
