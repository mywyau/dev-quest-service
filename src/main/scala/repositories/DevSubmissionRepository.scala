package repositories

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import configuration.AppConfig
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.Open
import models.database.*
import models.quests.*
import models.uploads.DevSubmissionMetadata
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

trait DevSubmissionRepositoryAlgebra[F[_]] {

  def getFileMetaData(s3ObjectKey: String): F[Option[DevSubmissionMetadata]]

  def getAllFileMetaData(questId: String): F[List[DevSubmissionMetadata]]

  def createFileMetaData(devSubmissionMetadata: DevSubmissionMetadata): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class DevSubmissionRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F], appConfig: AppConfig) extends DevSubmissionRepositoryAlgebra[F] {

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)
  implicit val instantTimeMeta: Meta[Instant] = Meta[Timestamp].imap(_.toInstant())(Timestamp.from)

  override def getFileMetaData(s3ObjectKey: String): F[Option[DevSubmissionMetadata]] =
    val findQuery: F[Option[DevSubmissionMetadata]] =
      sql"""
         SELECT 
            client_id,
            dev_id,
            quest_id,
            file_name,
            file_type,
            file_size,
            s3_object_key,
            bucket_name
         FROM dev_submissions
         WHERE s3_object_key = $s3ObjectKey
       """.query[DevSubmissionMetadata].option.transact(transactor)

    findQuery

  override def getAllFileMetaData(questId: String): F[List[DevSubmissionMetadata]] =
    val findQuery: F[List[DevSubmissionMetadata]] =
      sql"""
         SELECT 
            client_id,
            dev_id,
            quest_id,
            file_name,
            file_type,
            file_size,
            s3_object_key,
            bucket_name
         FROM dev_submissions
         WHERE quest_id = $questId
       """.query[DevSubmissionMetadata].to[List].transact(transactor)

    findQuery

  override def createFileMetaData(devSubmissionMetadata: DevSubmissionMetadata): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val expiresAt: Instant = Instant.now().plus(Duration.ofDays(appConfig.devSubmission.expiryDays))

    sql"""
    INSERT INTO dev_submissions (
       client_id,
       dev_id,
       quest_id,
       file_name,
       file_type,
       file_size,
       s3_object_key,
       bucket_name,
       expires_at
    )
    VALUES (
      ${devSubmissionMetadata.clientId},
      ${devSubmissionMetadata.devId},
      ${devSubmissionMetadata.questId},
      ${devSubmissionMetadata.fileName},
      ${devSubmissionMetadata.fileType},
      ${devSubmissionMetadata.fileSize},
      ${devSubmissionMetadata.s3ObjectKey},
      ${devSubmissionMetadata.bucketName},
      $expiresAt
    )
  """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          CreateSuccess.validNel
        case Left(_: java.sql.SQLIntegrityConstraintViolationException) =>
          ConstraintViolation.invalidNel
        case Left(_: java.sql.SQLException) =>
          DatabaseConnectionError.invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }
  }
}

object DevSubmissionRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F], appConfig: AppConfig): DevSubmissionRepositoryAlgebra[F] =
    new DevSubmissionRepositoryImpl[F](transactor, appConfig)
}
