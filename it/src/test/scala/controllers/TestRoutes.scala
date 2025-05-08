package controllers

import cache.RedisCacheAlgebra
import cache.RedisCacheImpl
import cats.effect.*
import cats.implicits.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.QuestRepository
import services.QuestService

import scala.concurrent.duration.*

object TestRoutes {

  class MockRedisCache(ref: Ref[IO, Map[String, String]]) extends RedisCacheAlgebra[IO] {

    override def deleteSession(userId: String): IO[Long] = ???

    def storeSession(userId: String, token: String): IO[Unit] =
      ref.update(_.updated(s"auth:session:$userId", token))

    def getSession(userId: String): IO[Option[String]] =
      ref.get.map(_.get(s"auth:session:$userId"))
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

  def createTestRouter(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {
    val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.integrationSpecConfig._3.host)
    val redisPort = sys.env.get("REDIS_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(appConfig.integrationSpecConfig._3.port)

    questRoutes(transactor, appConfig).map { questRoute =>
      Router(
        "/" -> (baseRoutes() <+> authRoutes(redisHost, redisPort, appConfig)),
        "/dev-quest-service" -> questRoute
      )
    }
  }

}
