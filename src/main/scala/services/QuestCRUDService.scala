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
import models.NotStarted
import models.QuestStatus
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.languages.Language
import models.quests.*
import models.skills.Questing
import org.typelevel.log4cats.Logger
import repositories.*
import services.LevelServiceAlgebra


import java.util.UUID

trait QuestCRUDServiceAlgebra[F[_]] {

  def getByQuestId(questId: String): F[Option[QuestPartial]]

  def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def completeQuestAwardXp(questId: String, questStatus: QuestStatus, rank: Rank): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestCRUDServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  questRepo: QuestRepositoryAlgebra[F],
  userRepo: UserDataRepositoryAlgebra[F],
  levelService: LevelServiceAlgebra[F]
) extends QuestCRUDServiceAlgebra[F] {

  def xpAmount(rank: Rank) =
    rank match {
      case Bronze => appConfig.questConfig.bronzeXp
      case Iron => appConfig.questConfig.ironXp
      case Steel => appConfig.questConfig.steelXp
      case Mithril => appConfig.questConfig.mithrilXp
      case Adamantite => appConfig.questConfig.adamantiteXp
      case Runic => appConfig.questConfig.runicXp
      case Demon => appConfig.questConfig.demonXp
      case Ruinous => appConfig.questConfig.ruinousXp
      case Aether => appConfig.questConfig.aetherXp
    }

  override def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.updateStatus(questId, questStatus)

  override def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val MAX_ACTIVE_QUESTS = appConfig.questConfig.maxActiveQuests

    for {
      activeCount <- questRepo.countActiveQuests(devId)
      result <-
        if (activeCount >= MAX_ACTIVE_QUESTS)
          TooManyActiveQuestsError.invalidNel[DatabaseSuccess].pure[F]
        else
          questRepo.acceptQuest(questId, devId)
    } yield result
  }

  // override def getAllQuests(clientId: String): F[List[QuestPartial]] =
  //   questRepo.findAllByUserId(clientId).flatMap { quests =>
  //     Logger[F].debug(s"[QuestCRUDService][getAllQuests] Retrieved ${quests.size} quests for user $clientId") *>
  //       Concurrent[F].pure(quests)
  //   }

  override def getByQuestId(questId: String): F[Option[QuestPartial]] =
    questRepo.findByQuestId(questId).flatMap {
      case Some(quest) =>
        Logger[F].debug(s"[QuestCRUDService][getByQuestId] Found quest with ID: $questId") *> Concurrent[F].pure(Some(quest))
      case None =>
        Logger[F].debug(s"[QuestCRUDService][getByQuestId] No quest found with ID: $questId") *> Concurrent[F].pure(None)
    }

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

    Logger[F].debug(s"[QuestCRUDService][create] Creating a new quest for user $clientId with questId $newQuestId") *>
      questRepo.create(createQuest).flatMap {
        case Valid(value) =>
          Logger[F].debug(s"[QuestCRUDService][create] Quest created successfully with ID: $newQuestId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[QuestCRUDService][create] Failed to create quest. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }
  }

  override def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.update(questId, request).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[QuestCRUDService][update] Successfully updated quest with ID: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestCRUDService][update] Failed to update quest with ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }

  override def completeQuestAwardXp(
    questId: String,
    questStatus: QuestStatus,
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val xp = xpAmount(rank)

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
        Logger[F].debug(s"[QuestCRUDService][delete] Successfully deleted quest with ID: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestCRUDService][delete] Failed to delete quest with ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }
}

object QuestCRUDService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    questRepo: QuestRepositoryAlgebra[F],
    userRepo: UserDataRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F]
  ): QuestCRUDServiceAlgebra[F] =
    new QuestCRUDServiceImpl[F](appConfig, questRepo, userRepo, levelService)
}
