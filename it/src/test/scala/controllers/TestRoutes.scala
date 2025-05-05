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

  // class StubRedisCommands(ref: Ref[IO, Map[String, String]]) extends RedisCommands[IO, String, String] {
  //   override def get(key: String): IO[Option[String]] =
  //     ref.get.map(_.get(key))

  //   override def setEx(key: String, value: String, expiration: FiniteDuration): IO[Unit] =
  //     ref.update(_.updated(key, value))

  //   override def set(key: String, value: String): IO[Unit] = setEx(key, value, 1.hour)

  //   override def ping: IO[String] = IO.pure("PONG")

  //   // You must override these:
  //   // override def del(key: String): IO[Long] = IO.pure(1L)
  //   // override def isOpen: IO[Boolean] = IO.pure(true)
  //   // override def close: IO[Unit] = IO.unit

  //   // Stub out unused functionality
  //   override def auth(password: CharSequence): IO[Boolean] = IO.raiseError(new NotImplementedError())
  //   override def auth(username: String, password: CharSequence): IO[Boolean] = IO.raiseError(new NotImplementedError())
  //   override def disableAutoFlush: IO[Unit] = IO.unit
  //   override def enableAutoFlush: IO[Unit] = IO.unit
  //   override def flushCommands: IO[Unit] = IO.unit
  // }

  // class MockRedisCache(ref: Ref[IO, Map[String, String]]) extends RedisCacheAlgebra[IO] {

  //   override def redisResource: Resource[IO, RedisCommands[IO, String, String]] =
  //     Resource.pure(new StubRedisCommands(ref))
  //     // Resource.pure(new StubRedisCommands)

  //   override def storeSession(redis: RedisCommands[IO, String, String], token: String, userId: String): IO[Unit] =
  //     redis.setEx(s"auth:session:$token", userId, 1.hour)

  //   override def validateSession(redis: RedisCommands[IO, String, String], token: String): IO[Option[String]] =
  //     redis.get(s"auth:session:$token")
  // }

  class MockRedisCache(ref: Ref[IO, Map[String, String]]) extends RedisCacheAlgebra[IO] {
    def storeSession(token: String, userId: String): IO[Unit] =
      ref.update(_.updated(s"auth:session:$token", userId))

    def validateSession(token: String): IO[Option[String]] =
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
