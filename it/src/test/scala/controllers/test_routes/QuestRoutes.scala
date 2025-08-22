package controllers.test_routes

import cache.SessionCacheAlgebra
import cache.SessionCacheImpl
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.*
import cats.implicits.*
import configuration.AppConfig
import configuration.BaseAppConfig
import controllers.QuestController
import controllers.mocks.*
import controllers.test_routes.AuthRoutes.*
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.transactor.Transactor
import models.auth.UserSession
import models.cache.*
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.server.Router
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.*
import services.*

import java.net.URI
import java.time.Duration
import java.time.Instant

object QuestRoutes extends BaseAppConfig {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

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
            s"auth:session:USER006" -> fakeUserSession("USER006"),
            s"auth:session:USER007" -> fakeUserSession("USER007")
          )
        )
      )
      mockSessionCache = new MockSessionCache(ref)
      rewardRepository = RewardRepository(transactor)
      questRepository = QuestRepository(transactor)
      userDataRepository = UserDataRepository(transactor)
      skillDataRepository = DevSkillRepository(transactor)
      languageRepository = DevLanguageRepository(transactor)
      hoursWorkedRepository = HoursWorkedRepository(transactor)
      levelService = LevelService(skillDataRepository, languageRepository)
      questCRUDService = QuestCRUDService(appConfig, questRepository, userDataRepository, hoursWorkedRepository, levelService)
      questStreamingService = QuestStreamingService(appConfig, questRepository, rewardRepository)
      questController = QuestController(questCRUDService, questStreamingService, mockSessionCache)
    } yield questController.routes
  }
}
