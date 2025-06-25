package services

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import fs2.Stream
import java.util.UUID
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.rewards.*
import org.typelevel.log4cats.Logger
import repositories.RewardRepositoryAlgebra

trait RewardServiceAlgebra[F[_]] {

  def getReward(questId: String): F[Option[RewardData]]

  def createReward(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateReward(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class RewardServiceImpl[F[_] : Concurrent : Monad : Logger](
  rewardRepo: RewardRepositoryAlgebra[F]
) extends RewardServiceAlgebra[F] {

  override def getReward(questId: String): F[Option[RewardData]] =
    rewardRepo.getRewardData(questId).flatMap {
      case Some(reward) =>
        Logger[F].info(s"[RewardService] Found reward data for quest with questId: $questId") *>
          Concurrent[F].pure(Some(reward))
      case None =>
        Logger[F].info(s"[RewardService] No reward data found for quest with questId: $questId") *>
          Concurrent[F].pure(None)
    }

  override def createReward(clientId: String, request: CreateReward): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    Logger[F].info(s"[RewardService][createReward] Creating a new reward data for quest with questId ${request.questId}") *>
      rewardRepo.create(clientId, request).flatMap {
        case Valid(value) =>
          Logger[F].info(s"[QuestService][createReward] Successfully created reward data for quest with questId: ${request.questId}") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[QuestService][createReward] Failed to create reward data. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }

  override def updateReward(questId: String, request: UpdateRewardData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    Logger[F].info(s"[RewardService][createReward] Updating reward data for quest with questId ${questId}") *>
      rewardRepo.update(questId, request).flatMap {
        case Valid(value) =>
          Logger[F].info(s"[QuestService][createReward] Successfully UPDATED reward data for quest with questId: ${questId}") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[QuestService][createReward] Failed to UPDATE reward data. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }

}

object RewardService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](rewardRepo: RewardRepositoryAlgebra[F]): RewardServiceAlgebra[F] =
    new RewardServiceImpl[F](rewardRepo)
}
