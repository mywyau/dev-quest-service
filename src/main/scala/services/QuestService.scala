package services

import cats.data.Validated
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.Monad
import cats.NonEmptyParallel
import fs2.Stream
import java.util.UUID
import models.database.*
import models.quests.CreateQuest
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import models.NotStarted
import repositories.QuestRepositoryAlgebra

trait QuestServiceAlgebra[F[_]] {

  def streamByUserId(userId: String, limit: Int, offset: Int): Stream[F, QuestPartial]

  def getAllQuests(userId: String): F[List[QuestPartial]]

  def getByQuestId(questId: String): F[Option[QuestPartial]]

  def create(questRequest: CreateQuestPartial, userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad](
  questRepo: QuestRepositoryAlgebra[F]
) extends QuestServiceAlgebra[F] {

  def streamByUserId(userId: String, limit: Int, offset: Int): Stream[F, QuestPartial] =
    println(questRepo.streamByUserId(userId, limit, offset))
    questRepo.streamByUserId(userId, limit, offset)

  override def getAllQuests(userId: String): F[List[QuestPartial]] =
    questRepo.findAllByUserId(userId)

  override def getByQuestId(questId: String): F[Option[QuestPartial]] =
    questRepo.findByQuestId(questId)

  override def create(request: CreateQuestPartial, userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val newQuestId = s"quest-${UUID.randomUUID().toString}"
    val createQuest =
      CreateQuest(
        userId = userId,
        questId = newQuestId,
        title = request.title,
        description = request.description,
        status = Some(NotStarted)
      )

    questRepo.create(createQuest)
  }

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
