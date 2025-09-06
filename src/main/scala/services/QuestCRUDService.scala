package services

import cats.Monad
import cats.NonEmptyParallel
import cats.data.EitherT
import cats.data.NonEmptyList
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
import models.events.QuestCreatedEvent
import models.kafka.*
import models.skills.Questing
import models.work_time.HoursOfWork
import org.typelevel.log4cats.Logger
import repositories.*
import services.LevelServiceAlgebra
import services.kafka.producers.QuestEventProducerAlgebra

import java.util.UUID

import models.languages.Language
import models.quests.*

trait QuestCRUDServiceAlgebra[F[_]] {

  def getByQuestId(questId: String): F[Option[QuestPartial]]

  def countNotEstimatedAndOpenQuests(): F[Int]

  // def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[Failure, KafkaProducerResult]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def completeQuestAwardXp(questId: String, questStatus: QuestStatus, rank: Rank): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def getHoursOfWork(questId: String): F[Option[HoursOfWork]]

  def upsertHoursOfWork(clientId: String, questId: String, request: HoursOfWork): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestCRUDServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  questRepo: QuestRepositoryAlgebra[F],
  userRepo: UserDataRepositoryAlgebra[F],
  hoursWorkedRepo: HoursWorkedRepositoryAlgebra[F],
  levelService: LevelServiceAlgebra[F],
  questEventProducer: QuestEventProducerAlgebra[F]
) extends QuestCRUDServiceAlgebra[F] {

  def xpAmount(rank: Rank) =
    rank match {
      case Bronze => appConfig.questConfig.bronzeXp
      case Iron => appConfig.questConfig.ironXp
      case Steel => appConfig.questConfig.steelXp
      case Mithril => appConfig.questConfig.mithrilXp
      case Adamantite => appConfig.questConfig.adamantiteXp
      case Rune => appConfig.questConfig.runicXp
      case Demonic => appConfig.questConfig.demonicXp
      case Ruin => appConfig.questConfig.ruinXp
      case Aether => appConfig.questConfig.aetherXp
      case _ => 0
    }

  override def countNotEstimatedAndOpenQuests(): F[Int] =
    questRepo.countNotEstimatedAndOpenQuests().flatMap { numberOfQuests =>
      Logger[F].debug(s"[QuestCRUDService][countNotEstimatedAndOpenQuests] Total number of quests found $numberOfQuests") *> Concurrent[F].pure(numberOfQuests)
    }

  override def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    questRepo.updateStatus(questId, questStatus)

  override def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val MAX_ACTIVE_QUESTS = appConfig.questConfig.maxActiveQuests

    for {
      activeCount <- questRepo.countActiveQuests(devId)
      questOpt <- questRepo.findByQuestId(questId)
      result <- questOpt match {
        case None =>
          NotFoundError.invalidNel[DatabaseSuccess].pure[F]

        case Some(quest) if activeCount > MAX_ACTIVE_QUESTS =>
          TooManyActiveQuestsError.invalidNel[DatabaseSuccess].pure[F]

        case Some(quest) if quest.status.contains(NotEstimated) =>
          QuestNotEstimatedError.invalidNel[DatabaseSuccess].pure[F]

        case Some(_) =>
          questRepo.acceptQuest(questId, devId)
      }
    } yield result
  }

  override def getByQuestId(questId: String): F[Option[QuestPartial]] =
    questRepo.findByQuestId(questId).flatMap {
      case Some(quest) =>
        Logger[F].debug(s"[QuestCRUDService][getByQuestId] Found quest with ID: $questId") *> Concurrent[F].pure(Some(quest))
      case None =>
        Logger[F].debug(s"[QuestCRUDService][getByQuestId] No quest found with ID: $questId") *> Concurrent[F].pure(None)
    }

  // override def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[Failure, KafkaProducerResult]] = {

  //   val newQuestId = s"quest-${UUID.randomUUID().toString}"
  //   val now        = java.time.Instant.now()

  //   val createQuest =
  //     CreateQuest(
  //       clientId = clientId,
  //       questId = newQuestId,
  //       rank = request.rank,
  //       title = request.title,
  //       description = request.description,
  //       acceptanceCriteria = request.acceptanceCriteria,
  //       tags = request.tags,
  //       status = Some(NotEstimated)
  //     )

  //   Logger[F].debug(s"[QuestCRUDService][create] Creating a new quest for user $clientId with questId $newQuestId") *>

  //     questRepo.create(createQuest).flatMap {
  //       case v @ Valid(value) =>

  //         val ev = models.events.QuestCreatedEvent(
  //           questId   = newQuestId,
  //           title     = request.title,
  //           clientId  = clientId,
  //           createdAt = now
  //         )
  //         // fire-and-forget in the same effect (no blocking the HTTP path)
  //         questEventProducer
  //           .publishQuestCreated(ev)
  //           .handleErrorWith(e =>
  //             Logger[F].warn(e)(s"[QuestCRUDService][create] Failed to publish quest.created for $newQuestId") *>
  //               Concurrent[F].pure(FailedWrite(e.getMessage))
  //           )
  //           .map(Valid(_))

  //       case Invalid(errors) =>
  //         Logger[F].error(s"[QuestCRUDService][create] Failed to create quest. Errors: ${errors.toList.mkString(", ")}") *>
  //           Concurrent[F].pure(Invalid(FailedWrite("")))
  //     }
  // }

  override def create(request: CreateQuestPartial, clientId: String): F[ValidatedNel[Failure, KafkaProducerResult]] = {
    val newQuestId = s"quest-${UUID.randomUUID().toString}"
    val now = java.time.Instant.now()

    val createQuest = CreateQuest(
      clientId = clientId,
      questId = newQuestId,
      rank = request.rank,
      title = request.title,
      description = request.description,
      acceptanceCriteria = request.acceptanceCriteria,
      tags = request.tags,
      status = Some(NotEstimated)
    )

    for {
      _ <- Logger[F].debug(s"[QuestCRUDService][create] Creating new quest $newQuestId")

      dbResult <- questRepo.create(createQuest)

      result <- dbResult match {
        case Valid(_) =>
          val event = QuestCreatedEvent(
            questId = newQuestId,
            title = request.title,
            clientId = clientId,
            createdAt = now
          )

          questEventProducer
            .publishQuestCreated(event)
            .map(Valid(_))
            .handleErrorWith(e =>
              Logger[F].warn(e)(s"[QuestCRUDService][create] Failed to publish event for $newQuestId") *>
                Concurrent[F].pure(Invalid(NonEmptyList.one(KafkaSendError(e.getMessage))))
            )

        case Invalid(dbErrors) =>
          Logger[F].error(s"[QuestCRUDService][create] DB error: ${dbErrors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(dbErrors.map(e => DatabaseFailure(e.toString))))
      }
    } yield result
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
      quest <- EitherT.fromOptionF(questRepo.findByQuestId(questId), NotFoundError: DatabaseErrors)

      _ <- EitherT.liftF(questRepo.updateStatus(questId, Completed))

      devId <- EitherT.fromOption[F](quest.devId, ForeignKeyViolationError: DatabaseErrors)

      user <- EitherT.fromOptionF(userRepo.findUser(devId), NotFoundError: DatabaseErrors)

      username = user.username
      tags = quest.tags

      _ <- EitherT.liftF(levelService.awardSkillXpWithLevel(devId, username, Questing, xp))
      xpToAward = if (tags.length == 1) xp else (xp / tags.length) * 0.8
      _ <- EitherT.liftF(tags.traverse { tag =>
        levelService.awardLanguageXpWithLevel(devId, username, Language.fromString(tag), xpToAward)
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

  override def getHoursOfWork(questId: String): F[Option[HoursOfWork]] =
    hoursWorkedRepo.getHoursOfWork(questId)

  override def upsertHoursOfWork(clientId: String, questId: String, request: HoursOfWork): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    hoursWorkedRepo.upsertHoursOfWork(clientId, questId, request).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[QuestCRUDService][upsertHoursOfWork] Successfully added/updated the number hours of work for the quest: $questId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[QuestCRUDService][upsertHoursOfWork] Failed to add/update the number hours of work for the quest ID: $questId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }
}

object QuestCRUDService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    questRepo: QuestRepositoryAlgebra[F],
    userRepo: UserDataRepositoryAlgebra[F],
    hoursWorkedRepo: HoursWorkedRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F],
    questEventProducer: QuestEventProducerAlgebra[F]
  ): QuestCRUDServiceAlgebra[F] =
    new QuestCRUDServiceImpl[F](appConfig, questRepo, userRepo, hoursWorkedRepo, levelService, questEventProducer)
}
