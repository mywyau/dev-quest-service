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

trait EstimateServiceAlgebra[F[_]] {

  def getEstimates(questId: String): F[GetEstimateResponse]

  def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class EstimateServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  userDataRepo: UserDataRepositoryAlgebra[F],
  estimateRepo: EstimateRepositoryAlgebra[F]
) extends EstimateServiceAlgebra[F] {

  private def calculateQuestDifficultyAndRank(
    score: Int,
    days: BigDecimal
  ): Rank = {
    // Normalize score and days (e.g. max 100 score, assume 1–30 days range)
    val normalizedScore = BigDecimal(score) / 100
    val normalizedDays = days / 30

    val weightedScore = (normalizedScore + normalizedDays) / 2

    val rank =
      weightedScore match {
        case w if w < 0.2 => Bronze
        case w if w < 0.4 => Iron
        case w if w < 0.6 => Steel
        case w if w < 0.8 => Mithril
        case w if w < 0.95 => Adamantite
        case _ => Runic
      }

    rank
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
      if(calculatedEstimates.size >= 3)
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
                  Logger[F].debug(s"[EstimateService][create] Estimate created successfully") *>
                    Concurrent[F].pure(Valid(value))
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
    userDataRepo: UserDataRepositoryAlgebra[F],
    estimateRepo: EstimateRepositoryAlgebra[F]
  ): EstimateServiceAlgebra[F] =
    new EstimateServiceImpl[F](userDataRepo, estimateRepo)
}
