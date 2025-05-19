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
import models.quests.CreateQuest
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import models.NotStarted
import org.typelevel.log4cats.Logger
import repositories.QuestRepositoryAlgebra

trait QuestServiceAlgebra[F[_]] {

  def streamByUserId(userId: String, limit: Int, offset: Int): Stream[F, QuestPartial]

  def getAllQuests(userId: String): F[List[QuestPartial]]

  def getByQuestId(questId: String): F[Option[QuestPartial]]

  def create(request: CreateQuestPartial, userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  questRepo: QuestRepositoryAlgebra[F]
) extends QuestServiceAlgebra[F] {

  // Log and stream quests by userId
  override def streamByUserId(
    userId: String,
    limit: Int,
    offset: Int
  ): Stream[F, QuestPartial] = {

    // A single-value stream that just performs the “start” log
    val headLog: Stream[F, QuestPartial] =
      Stream
        .eval(
          Logger[F].info(
            s"[QuestService] Streaming quests for user $userId (limit=$limit, offset=$offset)"
          )
        )
        .drain // drain: keep the effect, emit no element

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamByUserId(userId, limit, offset)
        .evalTap(q =>
          Logger[F].info(
            s"[QuestService] Fetched quest: ${q.questId}, title: ${q.title}"
          )
        )

    headLog ++ dataStream
  }

  override def getAllQuests(userId: String): F[List[QuestPartial]] =
    questRepo.findAllByUserId(userId).flatMap { quests =>
      Logger[F].info(s"[QuestService] Retrieved ${quests.size} quests for user $userId") *>
        Concurrent[F].pure(quests)
    }

  override def getByQuestId(questId: String): F[Option[QuestPartial]] =
    questRepo.findByQuestId(questId).flatMap {
      case Some(quest) =>
        Logger[F].info(s"[QuestService] Found quest with ID: $questId") *> Concurrent[F].pure(Some(quest))
      case None =>
        Logger[F].info(s"[QuestService] No quest found with ID: $questId") *> Concurrent[F].pure(None)
    }

  // Log quest creation
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

    Logger[F].info(s"[QuestService] Creating a new quest for user $userId with questId $newQuestId") *>
      questRepo.create(createQuest).flatMap {
        case Valid(value) =>
          Logger[F].info(s"[QuestService] Quest created successfully with ID: $newQuestId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[QuestService] Failed to create quest. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }
  }

  override def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.update(questId, request).flatMap {
      case Valid(value) =>
        Logger[F].info(s"[QuestService][update] Successfully updated quest with ID: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestService][update] Failed to update quest with ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }

  // Log quest deletion
  override def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.delete(questId).flatMap {
      case Valid(value) =>
        Logger[F].info(s"[QuestService] Successfully deleted quest with ID: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestService] Failed to delete quest with ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }
}

object QuestService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    questRepo: QuestRepositoryAlgebra[F]
  ): QuestServiceAlgebra[F] =
    new QuestServiceImpl[F](questRepo)
}
