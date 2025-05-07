package controllers

import cache.RedisCacheAlgebra
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

    override def deleteSession(token: String): IO[Long] = ???

    def storeSession(token: String, userId: String): IO[Unit] =
      ref.update(_.updated(s"auth:session:$token", userId))

    def getSession(token: String): IO[Option[String]] =
      ref.get.map(_.get(s"auth:session:$token"))
  }

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def baseRoutes(): HttpRoutes[IO] = {
    val baseController = BaseController[IO]()
    baseController.routes
  }

  def questRoutes(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] = {
    val sessionToken = "test-session-token"
    for {
      ref <- Resource.eval(Ref.of[IO, Map[String, String]](Map(s"auth:session:$sessionToken" -> "USER001")))
      mockRedisCache = new MockRedisCache(ref)
      questRepository = QuestRepository(transactor)
      questService = QuestService(questRepository)
      questController = QuestController(questService, mockRedisCache)
    } yield questController.routes
  }

  def createTestRouter(transactor: Transactor[IO], appConfig: AppConfig): Resource[IO, HttpRoutes[IO]] =
    questRoutes(transactor, appConfig).map { questRoute =>
      Router(
        "/" -> baseRoutes(),
        "/dev-quest-service" -> questRoute
      )
    }
}
