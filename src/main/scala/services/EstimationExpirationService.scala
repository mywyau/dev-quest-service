package services

import cats.Monad
import cats.NonEmptyParallel
import cats.data.EitherT
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import configuration.AppConfig
import fs2.Stream
import models.*
import models.database.*
import models.estimation_expirations.*
import org.typelevel.log4cats.Logger
import repositories.*

import java.util.UUID

trait EstimationExpirationServiceAlgebra[F[_]] {

  def getExpiration(questId: String): F[Option[EstimatedQuest]]
}

class EstimationExpirationServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  questRepo: QuestRepositoryAlgebra[F],
  estimationExpirationRepo: EstimationExpirationRepositoryAlgebra[F]
) extends EstimationExpirationServiceAlgebra[F] {

  override def getExpiration(questId: String): F[Option[EstimatedQuest]] =
    for {
      maybeQuest <- questRepo.findByQuestId(questId)
      maybeExpiration <- estimationExpirationRepo.getExpiration(questId)
      result = maybeQuest.flatMap(quest => maybeExpiration.map(expiration => EstimatedQuest(quest, expiration.estimationCloseAt)))
    } yield result
}

object EstimationExpirationService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    questRepo: QuestRepositoryAlgebra[F],
    estimationExpirationRepo: EstimationExpirationRepositoryAlgebra[F]
  ): EstimationExpirationServiceAlgebra[F] =
    new EstimationExpirationServiceImpl[F](appConfig, questRepo, estimationExpirationRepo)
}
