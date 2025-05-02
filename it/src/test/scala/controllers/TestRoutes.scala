package controllers

import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.QuestRepository
import services.QuestService

import java.time.LocalDateTime

object TestRoutes {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def baseRoutes(): HttpRoutes[IO] = {

    val baseController = BaseController[IO]()

    baseController.routes
  }

  def questRoutes(transactor: Transactor[IO]): HttpRoutes[IO] = {

    val questRepository = QuestRepository(transactor)

    val questService = QuestService(questRepository)
    val questController = QuestController(questService)

    questController.routes
  }

  def createTestRouter(transactor: Transactor[IO]): HttpRoutes[IO] =
    Router(
      "/" -> (baseRoutes()),
      "/dev-quest-service" -> (
        questRoutes(transactor)
      )
    )
}
