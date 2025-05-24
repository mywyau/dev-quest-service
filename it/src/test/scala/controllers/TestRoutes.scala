package controllers

import cache.RedisCacheAlgebra
import cache.RedisCacheImpl
import cache.SessionCacheAlgebra
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
import org.http4s.server.Router
import org.http4s.HttpRoutes
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repositories.*
import scala.concurrent.duration.*
import services.*

object TestRoutes {

  class MockRedisCache(ref: Ref[IO, Map[String, String]]) extends RedisCacheAlgebra[IO] {

    override def updateSession(userId: String, token: String): IO[Unit] =
      ref.update(_.updated(s"auth:session:$userId", token))

    override def deleteSession(userId: String): IO[Long] = ???

    def storeSession(userId: String, token: String): IO[Unit] =
      ref.update(_.updated(s"auth:session:$userId", token))

    def getSession(userId: String): IO[Option[String]] =
      ref.get.map(_.get(s"auth:session:$userId"))
  }

  class MockSessionCache(ref: Ref[IO, Map[String, String]]) extends SessionCacheAlgebra[IO] {

    override def getSession(userId: String): IO[Option[String]] =
      ref.get.map(_.get(s"auth:session:$userId"))

    override def updateSession(userId: String, session: Option[UserSession]): IO[ValidatedNel[CacheErrors, CacheSuccess]] =
      ref
        .update(_.updated(s"auth:session:$userId", session.map(_.cookieToken).getOrElse("no-cookie-available")))
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
    appConfig: AppConfig
  ): HttpRoutes[IO] = {

    val redisCache = new RedisCacheImpl[IO](redisHost, redisPort, appConfig)
    val authController = AuthController(redisCache)

    authController.routes
  }

  def questRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {
    val sessionToken = "test-session-token"
    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, String]](
          Map(
            s"auth:session:USER001" -> sessionToken,
            s"auth:session:USER002" -> sessionToken,
            s"auth:session:USER003" -> sessionToken,
            s"auth:session:USER004" -> sessionToken,
            s"auth:session:USER005" -> sessionToken,
            s"auth:session:USER006" -> sessionToken
          )
        )
      )
      mockRedisCache = new MockRedisCache(ref)
      questRepository = QuestRepository(transactor)
      questService = QuestService(questRepository)
      questController = QuestController(questService, mockRedisCache)
    } yield questController.routes
  }

  def userDataRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {

    val sessionToken = "test-session-token"
    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, String]](
          Map(
            s"auth:session:USER001" -> sessionToken,
            s"auth:session:USER002" -> sessionToken,
            s"auth:session:USER003" -> sessionToken,
            s"auth:session:USER004" -> sessionToken,
            s"auth:session:USER005" -> sessionToken,
            s"auth:session:USER006" -> sessionToken
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
    for {
      ref <- Resource.eval(
        Ref.of[IO, Map[String, String]](
          Map(
            s"auth:session:USER001" -> sessionToken,
            s"auth:session:USER002" -> sessionToken,
            s"auth:session:USER003" -> sessionToken,
            s"auth:session:USER004" -> sessionToken,
            s"auth:session:USER005" -> sessionToken,
            s"auth:session:USER006" -> sessionToken,
            s"auth:session:USER007" -> sessionToken,
            s"auth:session:USER008" -> sessionToken,
            s"auth:session:USER009" -> sessionToken,
            s"auth:session:USER010" -> sessionToken,
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
          authRoutes(redisHost, redisPort, appConfig) <+>
          questRoute <+>
          userDataRoutes <+>
          registrationRoutes
      )
    )
  }
}
