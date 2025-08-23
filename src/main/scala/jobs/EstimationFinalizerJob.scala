// jobs/EstimationFinalizerJob.scala

package jobs

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import configuration.AppConfig
import doobie.hikari.HikariTransactor
import fs2.Stream
import models.*
import models.database.*
import models.database.DatabaseErrors
import models.estimation_expirations.*
import models.quests.QuestPartial
import org.typelevel.log4cats.Logger
import repositories.*
import repositories.EstimationExpirationRepositoryAlgebra
import repositories.QuestRepositoryAlgebra
import services.EstimateServiceAlgebra

class EstimationFinalizerJob[F[_] : Concurrent : Logger : Clock](
  questRepo: QuestRepositoryAlgebra[F],
  expirationRepo: EstimationExpirationRepositoryAlgebra[F],
  estimateService: EstimateServiceAlgebra[F]
) {

  def run: F[Unit] =
    for {
      now <- Clock[F].realTimeInstant
      expiredQuests <- expirationRepo.getExpiredQuestIds(now)
      expiredIds = expiredQuests.map(_.questId).toSet

      questsValidated <- questRepo.findNotEstimatedQuests()

      _ <- questsValidated match {
        case Valid(ReadSuccess(quests)) =>
          quests.filter(q => expiredIds.contains(q.questId)).traverse_ { quest =>
            Logger[F].info(s"Finalizing quest ${quest.questId} after countdown expiration") *>
              estimateService.finalizeQuestEstimation(quest.questId).void
          }

        case Invalid(errors) =>
          Logger[F].warn(s"Finalizer encountered DB errors: ${errors.toList.mkString(", ")}")
      }

    } yield ()
}
