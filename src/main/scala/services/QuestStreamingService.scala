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
import configuration.AppConfig
import fs2.Stream
import java.util.UUID
import models.*
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.languages.Language
import models.quests.*
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
import services.LevelServiceAlgebra

trait QuestStreamingServiceAlgebra[F[_]] {

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

}

class QuestStreamingServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  questRepo: QuestRepositoryAlgebra[F],
  rewardRepo: RewardRepositoryAlgebra[F]
) extends QuestStreamingServiceAlgebra[F] {

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
            s"[QuestStreamingService][stream] Streaming quests for questStatus: $questStatus (limit=$limit, offset=$offset)"
          )
        )
        .drain // drain: keep the effect, emit no element

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamByQuestStatus(clientId, questStatus, limit, offset)
        .evalTap(quest =>
          Logger[F].debug(
            s"[QuestStreamingService][stream] Fetched quest: ${quest.questId}, title: ${quest.title}"
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
            s"[QuestStreamingService][stream] Streaming quests for questStatus: $questStatus (limit=$limit, offset=$offset)"
          )
        )
        .drain // drain: keep the effect, emit no element

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamByQuestStatusDev(devId, questStatus, limit, offset)
        .evalTap(quest =>
          Logger[F].debug(
            s"[QuestStreamingService][stream] Fetched quest: ${quest.questId}, title: ${quest.title}"
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
          Logger[F].debug(s"[QuestStreamingService] Streaming all quests with rewards (limit=$limit, offset=$offset)")
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
            s"[QuestStreamingService][streamAll] Streaming all quests (limit=$limit, offset=$offset)"
          )
        )
        .drain

    // The actual DB stream with per-row logging
    val dataStream: Stream[F, QuestPartial] =
      questRepo
        .streamAll(limit, offset)
        .evalTap(q =>
          Logger[F].debug(
            s"[QuestStreamingService][streamAll] Fetched quest: ${q.questId}, title: ${q.title}"
          )
        )

    headLog ++ dataStream
  }

  override def streamAllWithRewards(limit: Int, offset: Int): Stream[F, QuestWithReward] = {
    val headLog: Stream[F, QuestWithReward] =
      Stream
        .eval(
          Logger[F].debug(s"[QuestStreamingService] Streaming all quests with rewards (limit=$limit, offset=$offset)")
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
}

object QuestStreamingService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    questRepo: QuestRepositoryAlgebra[F],
    rewardRepo: RewardRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F]
  ): QuestStreamingServiceAlgebra[F] =
    new QuestStreamingServiceImpl[F](appConfig, questRepo, rewardRepo)
}
