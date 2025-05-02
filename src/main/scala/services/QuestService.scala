package services

import cats.data.Validated
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.Monad
import cats.NonEmptyParallel
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import repositories.QuestRepositoryAlgebra

trait QuestServiceAlgebra[F[_]] {

  def getByUserId(userId: String): F[Option[QuestPartial]]

  def getByQuestId(questId: String): F[Option[QuestPartial]]

  def create(questRequest: CreateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad](
  questRepo: QuestRepositoryAlgebra[F]
) extends QuestServiceAlgebra[F] {

  override def getByUserId(userId: String): F[Option[QuestPartial]] =
    questRepo.findByUserId(userId)

  override def getByQuestId(questId: String): F[Option[QuestPartial]] =
    questRepo.findByQuestId(questId)

  override def create(questRequest: CreateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.create(questRequest)

  override def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.update(questId, request)

  override def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.delete(questId)

}

object QuestService {

  def apply[F[_] : Concurrent : NonEmptyParallel](
    questRepo: QuestRepositoryAlgebra[F]
  ): QuestServiceAlgebra[F] =
    new QuestServiceImpl[F](questRepo)
}
