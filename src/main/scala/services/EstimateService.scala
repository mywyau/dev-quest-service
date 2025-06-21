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
import models.estimate.CreateEstimate
import models.estimate.Estimate
import org.typelevel.log4cats.Logger
import repositories.EstimateRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra

trait EstimateServiceAlgebra[F[_]] {

  def getEstimates(devId: String, questId: String): F[List[Estimate]]

  def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class EstimateServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  userDataRepo: UserDataRepositoryAlgebra[F],
  estimateRepo: EstimateRepositoryAlgebra[F]
) extends EstimateServiceAlgebra[F] {

  override def getEstimates(devId: String, questId: String): F[List[Estimate]] =
    for {
      estimates <- estimateRepo.getEstimates(questId)
      userOpt <- userDataRepo.findUser(devId)
      result = estimates.map { partial =>
        Estimate(
          username = userOpt.map(_.username).getOrElse("No username"),
          rank = partial.rank,
          comment = partial.comment
        )
      }
      _ <- Logger[F].info(s"[EstimateService][getEstimate] Returning ${result.length} estimates for quest $questId and dev $devId")
    } yield result

  override def createEstimate(devId: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val newEstimateId = s"estimate-${UUID.randomUUID().toString}"

    Logger[F].info(s"[EstimateService][create] Creating a new estimate for user $devId with estimateId $newEstimateId") *>
      estimateRepo.createEstimation(newEstimateId, devId, estimate).flatMap {
        case Valid(value) =>
          Logger[F].info(s"[EstimateService][create] Estimate created successfully with ID: $newEstimateId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[EstimateService][create] Failed to create estimate. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }
  }
}

object EstimateService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    userDataRepo: UserDataRepositoryAlgebra[F],
    estimateRepo: EstimateRepositoryAlgebra[F]
  ): EstimateServiceAlgebra[F] =
    new EstimateServiceImpl[F](userDataRepo, estimateRepo)
}
