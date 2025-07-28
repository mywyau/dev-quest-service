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
import models.quests.QuestPartial
import models.skills.Estimating
import models.users.*
import org.typelevel.log4cats.Logger
import repositories.EstimateRepositoryAlgebra
import repositories.QuestRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra

trait EstimateServiceAlgebra[F[_]] {

  def getEstimates(questId: String): F[GetEstimateResponse]

  def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def evaluateEstimates(questId: String): F[List[EvaluatedEstimate]]

  def completeEstimationAwardEstimatingXp(questId: String, rank: Rank): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def finalizeQuestEstimation(questId: String): F[Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess]]

  def finalizeExpiredEstimations(): F[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]]
}

class EstimateServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger : Clock](
  appConfig: AppConfig,
  userDataRepo: UserDataRepositoryAlgebra[F],
  estimateRepo: EstimateRepositoryAlgebra[F],
  questRepo: QuestRepositoryAlgebra[F],
  levelService: LevelServiceAlgebra[F]
) extends EstimateServiceAlgebra[F] {

  def isEstimated(quest: QuestPartial, now: Instant): Boolean =
    quest.estimationCloseAt.exists(_.isBefore(now))

  def xpAmount(rank: Rank): Double =
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
      case _ => 0
    }

  private def computeWeightedEstimate(score: Int, days: BigDecimal): BigDecimal = {
    val normalizedScore = BigDecimal(score) / 100
    val normalizedDays = (BigDecimal(math.log(days.toDouble + 1)) / math.log(31)).min(1.0)
    val alpha = BigDecimal(0.6)
    alpha * normalizedScore + (1 - alpha) * normalizedDays
  }

  private def computeCommunityAverage(estimates: List[Estimate]): Option[BigDecimal] =
    if estimates.nonEmpty then
      val total = estimates.map(e => computeWeightedEstimate(e.score, e.days)).sum
      Some(total / estimates.size)
    else None

  private def computeAccuracyModifier(
    userEstimate: Estimate,
    communityAvg: BigDecimal,
    tolerance: BigDecimal = 0.2
  ): BigDecimal = {
    val userWeighted = computeWeightedEstimate(userEstimate.score, userEstimate.days)
    val error = (userWeighted - communityAvg).abs
    val modifier = (1 - (error / tolerance)).min(0.5).max(-0.5) // clamp
    modifier
  }

  override def evaluateEstimates(questId: String): F[List[EvaluatedEstimate]] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      communityAvgOpt = computeCommunityAverage(estimates)
      result = communityAvgOpt match
        case Some(avg) =>
          estimates.map(e => EvaluatedEstimate(e, computeAccuracyModifier(e, avg)))
        case None =>
          estimates.map(e => EvaluatedEstimate(e, BigDecimal(0))) // default modifier
    } yield result

  // this can go in the quest/reward service
  def calculateEstimationXP(baseXP: Double, modifier: BigDecimal): Int =
    (BigDecimal(baseXP) * (1 + modifier)).toInt.max(0)

  private def rankFromWeightedScore(weightedScore: BigDecimal): Rank =
    weightedScore match {
      case w if w < 0.1 => Bronze
      case w if w < 0.2 => Iron
      case w if w < 0.3 => Steel
      case w if w < 0.4 => Mithril
      case w if w < 0.5 => Adamantite
      case w if w < 0.7 => Runic
      case w if w < 0.8 => Demon
      case w if w < 0.9 => Ruinous
      case _ => Aether
    }

  private def calculateQuestDifficultyAndRank(score: Int, days: BigDecimal): Rank =
    rankFromWeightedScore(computeWeightedEstimate(score, days))

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

  // private def computeEstimationCloseAt(
  //   now: Instant,
  //   bucketSeconds: Long,
  //   minWindowSeconds: Long
  // ): Instant = {

  //   val minimumWindow = Duration.ofHours(minWindowHours.toLong).getSeconds
  //   val twelveHours = Duration.ofHours(12).getSeconds
  //   val nowEpoch = now.getEpochSecond

  //   // Start at next bucket (12h aligned)
  //   var nextBucketEpoch = ((nowEpoch / twelveHours) + 1) * twelveHours

  //   // Keep moving forward until window is >= 23h
  //   while ((nextBucketEpoch - nowEpoch) < minimumWindow)
  //     nextBucketEpoch += twelveHours

  //   Instant.ofEpochSecond(nextBucketEpoch)
  // }

  private def computeEstimationCloseAt(
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

  private def startCountDown(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val minWindowSeconds = 
      if (appConfig.featureSwitches.localTesting) appConfig.estimationConfig.localMinimumEstimationWindowSeconds
      else appConfig.estimationConfig.prodMinimumEstimationWindowSeconds // or make this a config param too if needed


    val bucketSeconds =
        if (appConfig.featureSwitches.localTesting) appConfig.estimationConfig.localBucketSeconds
        else appConfig.estimationConfig.prodBucketSeconds 

    val now = Instant.now()
    val countdownEndsAt = computeEstimationCloseAt(now, bucketSeconds, minWindowSeconds)

    for {
      result <- questRepo.setEstimationCloseAt(questId, countdownEndsAt)
    } yield result
  }

  override def getEstimates(questId: String): F[GetEstimateResponse] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      maybeQuest: Option[QuestPartial] <- questRepo.findByQuestId(questId)
      questEstitmated: Boolean = maybeQuest.map(quest => isEstimated(quest, Instant.now())).getOrElse(false)
      calculatedEstimates = estimates.map(estimate =>
        CalculatedEstimate(
          username = estimate.username,
          score = estimate.score,
          days = estimate.days,
          rank = calculateQuestDifficultyAndRank(estimate.score, estimate.days),
          comment = estimate.comment
        )
      )
      _ <- Logger[F].debug(s"[EstimateService][getEstimate] Returning ${estimates.length} estimates for quest $questId")
    } yield
      if (calculatedEstimates.size >= appConfig.estimationConfig.estimationThreshold && questEstitmated)
        GetEstimateResponse(EstimateClosed, calculatedEstimates)
      else
        GetEstimateResponse(EstimateOpen, calculatedEstimates)

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
            awardXpResult: Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess] <- completeEstimationAwardEstimatingXp(questId, finalRank)
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
        allEstimates <- estimateRepo.getEstimates(estimate.questId)
        _ <-
          if (allEstimates.length == appConfig.estimationConfig.estimationThreshold)
            startCountDown(estimate.questId).void
          else
            Concurrent[F].unit
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
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val baseXp: Double = xpAmount(rank)

    val result =
      for {
        dbResponse: Validated[NonEmptyList[DatabaseErrors], DatabaseSuccess] <- questRepo.updateStatus(questId, Estimated)
        evaluated: List[EvaluatedEstimate] <- evaluateEstimates(questId)
        _ <-
          evaluated.traverse_ { case EvaluatedEstimate(estimate, modifier) =>
            val xp = calculateEstimationXP(baseXp, modifier)
            levelService.awardSkillXpWithLevel(estimate.devId, estimate.username, Estimating, xp)
          }
      } yield dbResponse

    result
  }

  override def finalizeExpiredEstimations(): F[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]] =
    for {
      now <- Clock[F].realTimeInstant
      expiredQuestsValidation <- questRepo.findQuestsWithExpiredEstimation(now)
      _ <- expiredQuestsValidation match {
        case Valid(ReadSuccess(quests: List[QuestPartial])) =>
          quests.traverse_ { quest =>
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
    } yield expiredQuestsValidation

}

object EstimateService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger : Clock](
    appConfig: AppConfig,
    userDataRepo: UserDataRepositoryAlgebra[F],
    estimateRepo: EstimateRepositoryAlgebra[F],
    questRepo: QuestRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F]
  ): EstimateServiceAlgebra[F] =
    new EstimateServiceImpl[F](appConfig, userDataRepo, estimateRepo, questRepo, levelService)
}
