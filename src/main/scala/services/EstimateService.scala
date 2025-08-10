package services

import cats.data.EitherT
import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Clock
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import configuration.AppConfig
import fs2.Stream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import models.*
import models.database.*
import models.estimate.*
import models.estimation_expirations.*
import models.quests.QuestPartial
import models.skills.Estimating
import models.users.*
import org.typelevel.log4cats.Logger
import repositories.EstimateRepositoryAlgebra
import repositories.EstimationExpirationRepositoryAlgebra
import repositories.QuestRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra

trait EstimateServiceAlgebra[F[_]] {

  def getEstimates(questId: String): F[GetEstimateResponse]

  def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def evaluateEstimates(questId: String, estimates:List[Estimate]): F[List[EvaluatedEstimate]]

  def completeEstimationAwardEstimatingXp(questId: String, rank: Rank, estimates: List[Estimate]): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def finalizeQuestEstimation(questId: String): F[Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess]]

  // def finalizeExpiredEstimations(): F[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]]. // possibly need to keep this implementation

  def finalizeExpiredEstimations(): F[Unit]
}

class EstimateServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger : Clock](
  appConfig: AppConfig,
  userDataRepo: UserDataRepositoryAlgebra[F],
  estimateRepo: EstimateRepositoryAlgebra[F],
  estimationExpirationRepo: EstimationExpirationRepositoryAlgebra[F],
  questRepo: QuestRepositoryAlgebra[F],
  levelService: LevelServiceAlgebra[F]
) extends EstimateServiceAlgebra[F] {

  def isEstimated(estimationExpiration: EstimationExpiration, now: Instant): Boolean =
    estimationExpiration.estimationCloseAt.exists(_.isBefore(now))

  def xpAmount(rank: Rank): Double =
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

  def computeWeightedEstimate(score: Int, hours: BigDecimal): BigDecimal = {
    val normalizedScore = BigDecimal(score) / 100
    val normalizedHours = (BigDecimal(math.log(hours.toDouble + 1)) / math.log(151)).min(1.0) // max hours is 150 for 20 days at 7.5 hours a day
    val alpha = BigDecimal(0.6)
    alpha * normalizedScore + (1 - alpha) * normalizedHours
  }

  private[services] def computeCommunityAverage(estimates: List[Estimate]): Option[BigDecimal] =
    if estimates.nonEmpty then
      val total = estimates.map(e => computeWeightedEstimate(e.score, e.hours)).sum
      Some(total / estimates.size)
    else None

  private[services] def computeAccuracyModifier(userEstimate: Estimate, communityAvg: BigDecimal, tolerance: BigDecimal = 0.2): F[BigDecimal] = {

    val userWeighted = computeWeightedEstimate(userEstimate.score, userEstimate.hours)
    val error = (userWeighted - communityAvg).abs
    val modifier = (1 - (error / tolerance)).min(0.5).max(-0.5)

    Logger[F]
      .debug(
        s"Modifier calculation for ${userEstimate.username}: userWeighted=$userWeighted, " +
          s"communityAvg=$communityAvg, error=$error, modifier=$modifier"
      ) *> modifier.pure[F]
  }

  override def evaluateEstimates(questId: String, estimates:List[Estimate]): F[List[EvaluatedEstimate]] =
    for {
      // estimates <- estimateRepo.getEstimates(questId)
      communityAvgOpt <- computeCommunityAverage(estimates).pure[F]
      result <- communityAvgOpt match {
        case Some(avg) =>
          estimates.traverse { e =>
            computeAccuracyModifier(e, avg).map(mod => EvaluatedEstimate(e, mod))
          }
        case None =>
          estimates.traverse(e => EvaluatedEstimate(e, BigDecimal(0)).pure[F])
      }
    } yield result

  // this can go in the quest/reward service
  def calculateEstimationXP(baseXP: Double, modifier: BigDecimal): Int =
    (BigDecimal(baseXP) * (1 + modifier)).toInt.max(0)

  private[services] def rankFromWeightedScore(weightedScore: BigDecimal): Rank =
    weightedScore match {
      case w if w < 0.1 => Bronze
      case w if w < 0.2 => Iron
      case w if w < 0.3 => Steel
      case w if w < 0.4 => Mithril
      case w if w < 0.5 => Adamantite
      case w if w < 0.7 => Rune
      case w if w < 0.8 => Demonic
      case w if w < 0.9 => Ruin
      case _ => Aether
    }

  private[services] def calculateQuestDifficultyAndRank(score: Int, hours: BigDecimal): Rank =
    rankFromWeightedScore(computeWeightedEstimate(score, hours))

  def setFinalRankFromEstimates(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      maybeAvg = computeCommunityAverage(estimates)
      result <- maybeAvg match
        case Some(avg) =>
          val finalRank = rankFromWeightedScore(avg)
          questRepo.setFinalRank(questId, finalRank)
        case None =>
          Logger[F].warn(s"No estimates found for quest $questId, cannot compute rank") *>
            Concurrent[F].pure(Invalid(NonEmptyList.one(NotEnoughEstimates)))
    } yield result

  private[services] def computeEstimationCloseAt(
    now: Instant,
    bucketSeconds: Long,
    minWindowSeconds: Long
  ): Instant = {
    val nowEpoch = now.getEpochSecond
    val firstBucket = ((nowEpoch / bucketSeconds) + 1) * bucketSeconds

    val nextBucketEpoch = Iterator
      .iterate(firstBucket)(_ + bucketSeconds)
      .dropWhile(_ - nowEpoch < minWindowSeconds)
      .next()

    Instant.ofEpochSecond(nextBucketEpoch)
  }

  private[services] def startCountDown(questId: String, clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val minWindowSeconds =
      if (appConfig.featureSwitches.localTesting) appConfig.estimationConfig.localMinimumEstimationWindowSeconds
      else appConfig.estimationConfig.prodMinimumEstimationWindowSeconds // or make this a config param too if needed

    val bucketSeconds =
      if (appConfig.featureSwitches.localTesting) appConfig.estimationConfig.localBucketSeconds
      else appConfig.estimationConfig.prodBucketSeconds

    val now = Instant.now()
    val countdownEndsAt = computeEstimationCloseAt(now, bucketSeconds, minWindowSeconds)

    for {
      // result <- questRepo.setEstimationCloseAt(questId, countdownEndsAt)
      result <- estimationExpirationRepo.upsertEstimationCloseAt(questId, clientId, countdownEndsAt)
    } yield result
  }

  override def getEstimates(questId: String): F[GetEstimateResponse] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      maybeEstimationExpiration <- estimationExpirationRepo.getExpiration(questId)
      maybeQuest: Option[QuestPartial] <- questRepo.findByQuestId(questId)
      questEstimated: Boolean = maybeEstimationExpiration.map(estimationExpiration => isEstimated(estimationExpiration, Instant.now())).getOrElse(false)
      calculatedEstimates =
        estimates.map(estimate =>
          CalculatedEstimate(
            username = estimate.username,
            score = estimate.score,
            hours = estimate.hours,
            rank = calculateQuestDifficultyAndRank(estimate.score, estimate.hours),
            comment = estimate.comment
          )
        )
      _ <- Logger[F].debug(s"[EstimateService][getEstimate] Returning ${estimates.length} estimates for quest $questId")
    } yield
      if (calculatedEstimates.size >= appConfig.estimationConfig.estimationThreshold && questEstimated)
        println(questEstimated)
        GetEstimateResponse(EstimateClosed, calculatedEstimates)
      else GetEstimateResponse(EstimateOpen, calculatedEstimates)

  override def finalizeQuestEstimation(questId: String): F[Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess]] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      _ <- Logger[F].debug(s"Estimates found: $estimates")
      maybeAvg = computeCommunityAverage(estimates)
      result: Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess] <- maybeAvg match
        case Some(avg) =>
          val finalRank = rankFromWeightedScore(avg)
          for {
            _ <- questRepo.setFinalRank(questId, finalRank)
            awardXpResult: Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess] <- completeEstimationAwardEstimatingXp(questId, finalRank, estimates)
          } yield awardXpResult
        case None =>
          Logger[F].warn(s"Unable to finalize estimation for quest $questId — no average found") *>
            Concurrent[F].pure(Invalid(NonEmptyList.one(NotFoundError)))
    } yield result

  override def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val newEstimateId = s"estimate-${UUID.randomUUID().toString}"

    def tooManyEstimatesError(count: Int): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
      Logger[F].warn(s"[EstimateService][create] User $devId exceeded estimate limit ($count today)") *>
        Concurrent[F].pure(Invalid(NonEmptyList.one(TooManyEstimatesToday)))

    def finalizeIfThresholdReached(): F[Unit] =
      for {
        questData <- questRepo.findByQuestId(estimate.questId)
        allEstimates <- estimateRepo.getEstimates(estimate.questId)
        _ <-
          questData match {
            case Some(quest) if allEstimates.length == appConfig.estimationConfig.estimationThreshold =>
              startCountDown(estimate.questId, quest.clientId).void
            case _ =>
              Concurrent[F].unit
          }
      } yield ()

    // create needs to check if the quest has finished estimating or if estimating is locked
    def createAndFinalize(user: UserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
      for {
        _ <- Logger[F].debug(s"[EstimateService][create] Creating a new estimate for user ${user.username} with ID $newEstimateId")
        result <- estimateRepo.createEstimation(newEstimateId, devId, user.username, estimate)
        outcome <- result match {
          case Valid(value) =>
            for {
              _ <- Logger[F].debug(s"[EstimateService][create] Estimate created successfully")
              _ <- finalizeIfThresholdReached()
            } yield Valid(value)
          case Invalid(errors) =>
            Logger[F].error(s"[EstimateService][create] Failed to create estimate: ${errors.toList.mkString(", ")}") *>
              Concurrent[F].pure(Invalid(errors))
        }
      } yield outcome

    for {
      userOpt <- userDataRepo.findUser(devId)
      result <- userOpt match {
        case Some(user) =>
          for {
            result <- createAndFinalize(user)
          } yield result

        case None =>
          Logger[F].error(s"[EstimateService][create] Could not find user with ID: $devId") *>
            Concurrent[F].pure(Invalid(NonEmptyList.one(NotFoundError)))
      }
    } yield result
  }

  override def completeEstimationAwardEstimatingXp(
    questId: String,
    rank: Rank,
    estimates: List[Estimate]
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val baseXp: Double = xpAmount(rank)

    val result =
      for {
        dbResponse: Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess] <- questRepo.updateStatus(questId, Estimated)
        evaluated: List[EvaluatedEstimate] <- evaluateEstimates(questId, estimates)
        _ <- evaluated.traverse_ { case EvaluatedEstimate(estimate, modifier) =>
          val xp = calculateEstimationXP(baseXp, modifier)
          Logger[F].info(
            s"Awarding $xp XP to ${estimate.username} (devId=${estimate.devId}) for Estimating. " +
              s"BaseXP=$baseXp, Modifier=$modifier, Score=${estimate.score}, Hours=${estimate.hours}"
          ) *>
            levelService.awardSkillXpWithLevel(devId = estimate.devId, username = estimate.username, skill = Estimating, xpToAdd = xp)
        }
      } yield dbResponse

    result
  }

  override def finalizeExpiredEstimations(): F[Unit] =
    for {
      now <- Clock[F].realTimeInstant
      expiredQuestsValidation: Validated[NonEmptyList[DatabaseErrors], ReadSuccess[List[QuestPartial]]] <- questRepo.findNotEstimatedQuests()
      expiredQuests: List[ExpiredQuests] <- estimationExpirationRepo.getExpiredQuestIds(now)
      expiredQuestIds: Set[String] = expiredQuests.map(_.questId).toSet
      _ <- expiredQuestsValidation match {
        case Valid(ReadSuccess(quests: List[QuestPartial])) =>
          val filteredQuests = quests.filter(q => expiredQuestIds.contains(q.questId))
          filteredQuests.traverse_ { quest =>
            for {
              _ <- Logger[F].info(s"Finalizing quest ${quest.questId} after countdown expiration")
              _ <- finalizeQuestEstimation(quest.questId)
            } yield ()
          }
        case Invalid(errors) =>
          Logger[F]
            .info(
              s"[EstimateServiceImpl][finalizeExpiredEstimations] SQL error, stream task might blow up. Errors: $errors"
            )
            .void
      }
    } yield ()

}

object EstimateService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger : Clock](
    appConfig: AppConfig,
    userDataRepo: UserDataRepositoryAlgebra[F],
    estimateRepo: EstimateRepositoryAlgebra[F],
    estimationExpirationRepo: EstimationExpirationRepositoryAlgebra[F],
    questRepo: QuestRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F]
  ): EstimateServiceAlgebra[F] =
    new EstimateServiceImpl[F](appConfig, userDataRepo, estimateRepo, estimationExpirationRepo, questRepo, levelService)
}
