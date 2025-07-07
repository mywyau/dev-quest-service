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
import fs2.Stream
import models.*
import models.database.*
import models.users.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.estimate.*
import org.typelevel.log4cats.Logger
import repositories.EstimateRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra

import java.util.UUID
import repositories.QuestRepositoryAlgebra
import configuration.AppConfig
import models.skills.Reviewing

trait EstimateServiceAlgebra[F[_]] {

  def getEstimates(questId: String): F[GetEstimateResponse]

  def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def evaluateEstimates(questId: String): F[List[EvaluatedEstimate]]

  def completeEstimationAwardReviewingXp(
    questId: String,
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class EstimateServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  userDataRepo: UserDataRepositoryAlgebra[F],
  estimateRepo: EstimateRepositoryAlgebra[F],
  questRepo: QuestRepositoryAlgebra[F], 
  levelService: LevelServiceAlgebra[F] 
) extends EstimateServiceAlgebra[F] {

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

  override def evaluateEstimates(questId: String): F[List[EvaluatedEstimate]] = {
    for {
      estimates <- estimateRepo.getEstimates(questId)
      communityAvgOpt = computeCommunityAverage(estimates)
      result = communityAvgOpt match
        case Some(avg) =>
          estimates.map(e => EvaluatedEstimate(e, computeAccuracyModifier(e, avg)))
        case None =>
          estimates.map(e => EvaluatedEstimate(e, BigDecimal(0))) // default modifier
    } yield result
  }

  // this can go in the quest/reward service
  def calculateEstimationXP(baseXP: Double, modifier: BigDecimal): Int =
    (BigDecimal(baseXP) * (1 + modifier)).toInt.max(0)


  private def rankFromWeightedScore(weightedScore: BigDecimal): Rank = weightedScore match {
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

  def setFinalRankFromEstimates(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
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
  }  


  override def getEstimates(questId: String): F[GetEstimateResponse] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      calculatedEstimates = estimates.map(estimate =>
        CalculatedEstimate(
          estimate.username,
          estimate.score,
          estimate.days,
          calculateQuestDifficultyAndRank(estimate.score, estimate.days), 
          estimate.comment
        )      
      )
      _ <- Logger[F].debug(s"[EstimateService][getEstimate] Returning ${estimates.length} estimates for quest $questId")
    } yield {
      if(calculatedEstimates.size >= appConfig.estimationConfig.estimationThreshold)
        GetEstimateResponse(EstimateClosed, calculatedEstimates)
      else 
        GetEstimateResponse(EstimateOpen, calculatedEstimates)
    }

  private def finalizeQuestEstimation(questId: String): F[Unit] = {
      for {
        estimates <- estimateRepo.getEstimates(questId)
        maybeAvg = computeCommunityAverage(estimates)
        _ <- maybeAvg match
          case Some(avg) =>
            val finalRank = rankFromWeightedScore(avg)
            for {
              _ <- questRepo.setFinalRank(questId, finalRank)
              _ <- completeEstimationAwardReviewingXp(questId, finalRank).void
            } yield ()
          case None =>
            Logger[F].warn(s"Unable to finalize estimation for quest $questId — no average found")
      } yield ()
    }  

  override def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val newEstimateId = s"estimate-${UUID.randomUUID().toString}"

    def tooManyEstimatesError(count: Int): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    Logger[F].warn(s"[EstimateService][create] User $devId exceeded daily estimate limit ($count today)") *>
      Concurrent[F].pure(Invalid(NonEmptyList.one(TooManyEstimatesToday)))

    def finalizeIfThresholdReached: F[Unit] =
      for {
        estimates <- estimateRepo.getEstimates(estimate.questId)
        _ <- if (estimates.length >= appConfig.estimationConfig.estimationThreshold)
               finalizeQuestEstimation(estimate.questId)
             else
               Concurrent[F].unit
      } yield ()

    def createAndFinalize(user: UserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
      for {
        _ <- Logger[F].debug(s"[EstimateService][create] Creating a new estimate for user ${user.username} with ID $newEstimateId")
        result <- estimateRepo.createEstimation(newEstimateId, devId, user.username, estimate)
        outcome <- result match {
          case Valid(value) =>
            for {
              _ <- Logger[F].debug(s"[EstimateService][create] Estimate created successfully")
              _ <- finalizeIfThresholdReached
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
            todayCount <- estimateRepo.countEstimatesToday(devId)
            result <- if (todayCount >= appConfig.estimationConfig.maxDailyReviews)
                        tooManyEstimatesError(todayCount)
                      else
                        createAndFinalize(user)
          } yield result

        case None =>
          Logger[F].error(s"[EstimateService][create] Could not find user with ID: $devId") *>
            Concurrent[F].pure(Invalid(NonEmptyList.one(NotFoundError)))
      }
    } yield result
  }

  override def completeEstimationAwardReviewingXp(
    questId: String,
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val baseXp: Double = xpAmount(rank)

    val result = for {
      quest <- EitherT.fromOptionF(questRepo.findByQuestId(questId), NotFoundError: DatabaseErrors)

      _ <- EitherT.liftF(questRepo.updateStatus(questId, Open))

      estimates <- EitherT.liftF(estimateRepo.getEstimates(questId))

      evaluated <- EitherT.liftF(evaluateEstimates(questId))

      _ <- EitherT.liftF {
        evaluated.traverse_ {
          case EvaluatedEstimate(estimate, modifier) =>
            val xp = calculateEstimationXP(baseXp, modifier)
            levelService.awardSkillXpWithLevel(estimate.devId, estimate.username, Reviewing, xp)
        }
      }

    } yield UpdateSuccess

    result.value.map(_.toValidatedNel)
  } 
}


object EstimateService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    userDataRepo: UserDataRepositoryAlgebra[F],
    estimateRepo: EstimateRepositoryAlgebra[F],
    questRepo: QuestRepositoryAlgebra[F],
    levelService: LevelServiceAlgebra[F] 
  ): EstimateServiceAlgebra[F] =
    new EstimateServiceImpl[F](appConfig, userDataRepo, estimateRepo, questRepo, levelService)
}
