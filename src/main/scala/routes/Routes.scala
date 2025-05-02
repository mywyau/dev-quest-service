package routes

import cats.NonEmptyParallel
import cats.effect.*
import controllers.*
import doobie.hikari.HikariTransactor
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.QuestService

object Routes {

  def baseRoutes[F[_] : Concurrent : Logger](): HttpRoutes[F] = {

    val baseController = BaseController()

    baseController.routes
  }

  def questsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val questRepository = QuestRepository(transactor)

    val questService = QuestService(questRepository)
    val questController = QuestController(questService)

    questController.routes
  }
}
