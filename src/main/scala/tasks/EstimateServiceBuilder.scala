package tasks

import cats.NonEmptyParallel
import cats.effect.*
import configuration.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.LevelService

import java.net.URI

object EstimateServiceBuilder {

  def build[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger : Clock](
    transactor: HikariTransactor[F],
    appConfig: AppConfig
  ): EstimateServiceAlgebra[F] = {

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val questRepository = QuestRepository(transactor)
    val estimateRepository = EstimateRepository(transactor)
    val skillDataRepository = SkillDataRepository(transactor)
    val languageRepository = LanguageRepository(transactor)

    val levelService = LevelService(skillDataRepository, languageRepository)
    val estimateService = EstimateService(appConfig, userDataRepository, estimateRepository, questRepository, levelService)

    estimateService
  }
}
