package jobs

import cats.effect.*
import cats.NonEmptyParallel
import configuration.AppConfig
import controllers.*
import doobie.hikari.HikariTransactor
import java.net.URI
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import repositories.*
import services.*
import services.LevelService
import services.kafka.producers.QuestEstimationEventProducerAlgebra

object EstimateServiceBuilder {

  def build[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger : Clock](
    transactor: HikariTransactor[F],
    appConfig: AppConfig,
    questEstimationEventProducer: QuestEstimationEventProducerAlgebra[F]
  ): EstimateServiceAlgebra[F] = {

    val userDataRepository = new UserDataRepositoryImpl(transactor)
    val questRepository = QuestRepository(transactor)
    val estimateRepository = EstimateRepository(transactor)
    val estimationExpirationRepository = EstimationExpirationRepository(transactor)
    val skillDataRepository = DevSkillRepository(transactor)
    val languageRepository = DevLanguageRepository(transactor)

    val levelService = LevelService(skillDataRepository, languageRepository)
    val estimateService = EstimateService(appConfig, userDataRepository, estimateRepository, estimationExpirationRepository, questRepository, levelService, questEstimationEventProducer)

    estimateService
  }
}
