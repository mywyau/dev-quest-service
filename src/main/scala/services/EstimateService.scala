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
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.estimate.*
import org.typelevel.log4cats.Logger
import repositories.EstimateRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra

import java.util.UUID
import repositories.QuestRepositoryAlgebra
import configuration.AppConfig

trait EstimateServiceAlgebra[F[_]] {

  def getEstimates(questId: String): F[GetEstimateResponse]

  def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def evaluateEstimates(questId: String): F[List[EvaluatedEstimate]]
}

class EstimateServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  userDataRepo: UserDataRepositoryAlgebra[F],
  estimateRepo: EstimateRepositoryAlgebra[F],
  questRepo: QuestRepositoryAlgebra[F] 
) extends EstimateServiceAlgebra[F] {

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
  def calculateEstimationXP(baseXP: Int, modifier: BigDecimal): Int =
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
          // .map(Valid(_))
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

  override def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val newEstimateId = s"estimate-${UUID.randomUUID().toString}"
  
    for {
      userOpt <- userDataRepo.findUser(devId)
      result <- userOpt match {
        case Some(user) =>
          Logger[F].debug(s"[EstimateService][create] Creating a new estimate for user ${user.username} with ID $newEstimateId") *>
            estimateRepo
              .createEstimation(newEstimateId, devId, user.username, estimate)
              .flatMap {
                case Valid(value) =>
                  for {
                    _ <- Logger[F].debug(s"[EstimateService][create] Estimate created successfully")
                    estimates <- estimateRepo.getEstimates(estimate.questId)
                    _ <- if (estimates.length >= appConfig.estimationConfig.estimationThreshold) {  // this value needs to go into app config
                      computeCommunityAverage(estimates) match {
                        case Some(avg) =>
                          val finalRank = rankFromWeightedScore(avg)
                          Logger[F].info(s"[EstimateService][create] Setting final rank $finalRank for quest ${estimate.questId}") *>
                            questRepo.setFinalRank(estimate.questId, finalRank)
                        case None =>
                          Logger[F].warn(s"[EstimateService][create] Unable to compute average for quest ${estimate.questId}") *>
                          Concurrent[F].pure(Invalid(NonEmptyList.one(UnableToCalculateEstimates)))
                      }
                    } else Concurrent[F].pure(Invalid(NonEmptyList.one(NotEnoughEstimates)))
                  } yield Valid(value)
  
                case Invalid(errors) =>
                  Logger[F].error(s"[EstimateService][create] Failed to create estimate: ${errors.toList.mkString(", ")}") *>
                    Concurrent[F].pure(Invalid(errors))
              }
  
        case None =>
          Logger[F].error(s"[EstimateService][create] Could not find user with ID: $devId") *>
            Concurrent[F].pure(Invalid(NonEmptyList.one(NotFoundError)))
      }
    } yield result
  }
}


object EstimateService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    userDataRepo: UserDataRepositoryAlgebra[F],
    estimateRepo: EstimateRepositoryAlgebra[F],
    questRepo: QuestRepositoryAlgebra[F] 
  ): EstimateServiceAlgebra[F] =
    new EstimateServiceImpl[F](appConfig, userDataRepo, estimateRepo, questRepo)
}
