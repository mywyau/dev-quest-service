package services

import cats.data.EitherT
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
import models.*
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.languages.Language
import models.quests.CreateQuest
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.QuestWithReward
import models.quests.UpdateQuestPartial
import models.skills.Questing
import models.NotStarted
import models.QuestStatus
import org.typelevel.log4cats.Logger
import repositories.LanguageRepositoryAlgebra
import repositories.QuestRepositoryAlgebra
import repositories.RewardRepositoryAlgebra
import repositories.SkillDataRepository
import repositories.SkillDataRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra

trait QuestServiceAlgebra[F[_]] {

  // streaming ND-JSON
  def streamClient(
    clientId: String,
    questStatus: QuestStatus,
    limit: Int,
    offset: Int
  ): Stream[F, QuestPartial]

  // streaming ND-JSON Dev
  def streamDev(
    devId: String,
    questStatus: QuestStatus,
    limit: Int,
    offset: Int
  ): Stream[F, QuestPartial]

  def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestWithReward]

  def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamAllWithRewards(limit: Int, offset: Int): Stream[F, QuestWithReward]

  def getByQuestId(questId: String): F[Option[QuestPartial]]

  def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def completeQuestAwardXp(questId: String, questStatus: QuestStatus, rank: Rank): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  questRepo: QuestRepositoryAlgebra[F],
  userRepo: UserDataRepositoryAlgebra[F],
  skillRepo: SkillDataRepositoryAlgebra[F],
  languageRepo: LanguageRepositoryAlgebra[F],
  rewardRepo: RewardRepositoryAlgebra[F],
  levelService: LevelServiceAlgebra[F]
) extends QuestServiceAlgebra[F] {

  override def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.updateStatus(questId, questStatus)

  override def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val MAX_ACTIVE_QUESTS = 5

    for {
      activeCount <- questRepo.countActiveQuests(devId)
      result <-
        if (activeCount >= MAX_ACTIVE_QUESTS)
          TooManyActiveQuestsError.invalidNel[DatabaseSuccess].pure[F]
        else
          questRepo.acceptQuest(questId, devId) // your existing method
    } yield result
  }

  override def streamClient(
    clientId: String,
    questStatus: QuestStatus,
    limit: Int,
    offset: Int
  ): Stream[F, QuestPartial] = {

    // A single-value stream that just performs the “start” log
    val headLog: Stream[F, QuestPartial] =
      Stream
        .eval(
          Logger[F].debug(
            s"[QuestService][stream] Streaming quests for questStatus: $questStatus (limit=$limit, offset=$offset)"
          )
        )
        .drain // drain: keep the effect, emit no element

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamByQuestStatus(clientId, questStatus, limit, offset)
        .evalTap(quest =>
          Logger[F].debug(
            s"[QuestService][stream] Fetched quest: ${quest.questId}, title: ${quest.title}"
          )
        )

    headLog ++ dataStream
  }

  override def streamDev(
    devId: String,
    questStatus: QuestStatus,
    limit: Int,
    offset: Int
  ): Stream[F, QuestPartial] = {

    // A single-value stream that just performs the “start” log
    val headLog: Stream[F, QuestPartial] =
      Stream
        .eval(
          Logger[F].debug(
            s"[QuestService][stream] Streaming quests for questStatus: $questStatus (limit=$limit, offset=$offset)"
          )
        )
        .drain // drain: keep the effect, emit no element

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamByQuestStatusDev(devId, questStatus, limit, offset)
        .evalTap(quest =>
          Logger[F].debug(
            s"[QuestService][stream] Fetched quest: ${quest.questId}, title: ${quest.title}"
          )
        )

    headLog ++ dataStream
  }

  // Log and stream quests by clientId
  override def streamByUserId(
    clientId: String,
    limit: Int,
    offset: Int
  ): Stream[F, QuestWithReward] = {

    val headLog: Stream[F, QuestWithReward] =
      Stream
        .eval(
          Logger[F].debug(s"[QuestService] Streaming all quests with rewards (limit=$limit, offset=$offset)")
        )
        .drain

    val enrichedQuests: Stream[F, QuestWithReward] =
      questRepo
        .streamByUserId(clientId, limit, offset)
        .evalMap { quest =>
          rewardRepo
            .streamRewardByQuest(quest.questId)
            .head // get only the first reward since it's 1:1
            .compile
            .last // or use `last` and handle Option
            .map(reward => QuestWithReward(quest, reward))
        }

    headLog ++ enrichedQuests
  }

  // Log and stream quests by clientId
  override def streamAll(
    limit: Int,
    offset: Int
  ): Stream[F, QuestPartial] = {

    // A single-value stream that just performs the “start” log
    val headLog: Stream[F, QuestPartial] =
      Stream
        .eval(
          Logger[F].debug(
            s"[QuestService][streamAll] Streaming all quests (limit=$limit, offset=$offset)"
          )
        )
        .drain

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamAll(limit, offset)
        .evalTap(q =>
          Logger[F].debug(
            s"[QuestService][streamAll] Fetched quest: ${q.questId}, title: ${q.title}"
          )
        )

    headLog ++ dataStream
  }

  override def streamAllWithRewards(limit: Int, offset: Int): Stream[F, QuestWithReward] = {
    val headLog: Stream[F, QuestWithReward] =
      Stream
        .eval(
          Logger[F].debug(s"[QuestService] Streaming all quests with rewards (limit=$limit, offset=$offset)")
        )
        .drain

    val enrichedQuests: Stream[F, QuestWithReward] =
      questRepo
        .streamAll(limit, offset)
        .evalMap { quest =>
          rewardRepo
            .streamRewardByQuest(quest.questId)
            .head // get only the first reward since it's 1:1
            .compile
            .last // or use `last` and handle Option
            .map(reward => QuestWithReward(quest, reward))
        }

    headLog ++ enrichedQuests
  }

  override def getByQuestId(questId: String): F[Option[QuestPartial]] =
    questRepo.findByQuestId(questId).flatMap {
      case Some(quest) =>
        Logger[F].debug(s"[QuestService][getByQuestId] Found quest with ID: $questId") *> Concurrent[F].pure(Some(quest))
      case None =>
        Logger[F].debug(s"[QuestService][getByQuestId] No quest found with ID: $questId") *> Concurrent[F].pure(None)
    }

  // Log quest creation
  override def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val newQuestId = s"quest-${UUID.randomUUID().toString}"
    val createQuest =
      CreateQuest(
        clientId = clientId,
        questId = newQuestId,
        rank = request.rank,
        title = request.title,
        description = request.description,
        acceptanceCriteria = request.acceptanceCriteria,
        tags = request.tags,
        status = Some(Open)
      )

    Logger[F].debug(s"[QuestService][create] Creating a new quest for user $clientId with questId $newQuestId") *>
      questRepo.create(createQuest).flatMap {
        case Valid(value) =>
          Logger[F].debug(s"[QuestService][create] Quest created successfully with ID: $newQuestId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[QuestService][create] Failed to create quest. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }
  }

  override def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.update(questId, request).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[QuestService][update] Successfully updated quest with ID: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestService][update] Failed to update quest with ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }

  override def completeQuestAwardXp(
    questId: String,
    questStatus: QuestStatus,
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val xp = rank match {
      case Bronze => 100
      case Iron => 300
      case Steel => 700
      case Mithril => 1000
      case Adamantite => 3000
      case Runic => 5000
      case Demon => 7000
      case Ruinous => 8000
      case Aether => 10000
    }

    val result = for {
      quest <- EitherT.fromOptionF(
        questRepo.findByQuestId(questId),
        NotFoundError: DatabaseErrors
      )

      _ <- EitherT.liftF(questRepo.updateStatus(questId, Completed))

      devId <- EitherT.fromOption[F](
        quest.devId,
        ForeignKeyViolationError: DatabaseErrors
      )

      user <- EitherT.fromOptionF(
        userRepo.findUser(devId),
        NotFoundError: DatabaseErrors
      )

      username = user.username
      tags = quest.tags
      
      _ <- EitherT.liftF(levelService.awardSkillXpWithLevel(devId, username, Questing, xp))
      _ <- EitherT.liftF(tags.traverse { tag =>
        levelService.awardLanguageXpWithLevel(devId, username, Language.fromString(tag), xp)
      })

    } yield UpdateSuccess

    result.value.map(_.toValidatedNel)
  }

  override def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.delete(questId).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[QuestService][delete] Successfully deleted quest with ID: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestService][delete] Failed to delete quest with ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }
}

object QuestService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    questRepo: QuestRepositoryAlgebra[F],
    userRepo: UserDataRepositoryAlgebra[F],
    skillRepo: SkillDataRepositoryAlgebra[F],
    languageRepo: LanguageRepositoryAlgebra[F],
    rewardRepo: RewardRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F]
  ): QuestServiceAlgebra[F] =
    new QuestServiceImpl[F](questRepo, userRepo, skillRepo, languageRepo, rewardRepo, levelService)
}
