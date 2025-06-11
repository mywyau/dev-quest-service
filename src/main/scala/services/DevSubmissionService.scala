package services

import cats.Monad
import cats.NonEmptyParallel
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import fs2.Stream
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.uploads.DevSubmissionMetadata
import org.typelevel.log4cats.Logger
import repositories.DevSubmissionRepositoryAlgebra

trait DevSubmissionServiceAlgebra[F[_]] {

  def getFileMetaData(questId: String):  F[List[DevSubmissionMetadata]]

  def createFileMetaData(devSubmissionMetadata: DevSubmissionMetadata): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class DevSubmissionServiceImpl[F[_] : Concurrent : Monad : Logger](
  devSubmissionRepo: DevSubmissionRepositoryAlgebra[F]
) extends DevSubmissionServiceAlgebra[F] {

  override def getFileMetaData(questId: String): F[List[DevSubmissionMetadata]] =
    devSubmissionRepo.getFileMetaData(questId)

  override def createFileMetaData(devSubmissionMetadata: DevSubmissionMetadata): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    devSubmissionRepo.createFileMetaData(devSubmissionMetadata)
}

object DevSubmissionService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    devSubmissionRepo: DevSubmissionRepositoryAlgebra[F]
  ): DevSubmissionServiceAlgebra[F] =
    new DevSubmissionServiceImpl[F](devSubmissionRepo)
}
