package repositories

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import configuration.AppConfig
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.implicits.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.database.*
import models.estimation_expirations.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime

trait EstimationExpirationRepositoryAlgebra[F[_]] {

  def getExpirationCount(questId: String): F[Int]

  def getExpiration(questId: String): F[Option[EstimationExpiration]]

  def getExpiredQuestIds(now: Instant): F[List[ExpiredQuests]]

  def getExpirations(questId: String, limit: Int, offset: Int): F[List[EstimationExpiration]]

  def upsertEstimationCloseAt(questId: String, clientId: String, closeAt: Instant): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def upsertExpirations(clientId: String, questId: String, expiration: EstimationExpiration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class EstimationExpirationRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends EstimationExpirationRepositoryAlgebra[F] {

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getExpirationCount(questId: String): F[Int] = {
    val findQuery: F[Int] =
      sql"""
        SELECT COUNT(*) 
        FROM estimation_expiration 
        WHERE quest_id = $questId
      """.query[Int].unique.transact(transactor)

    findQuery
  }

  override def getExpiration(questId: String): F[Option[EstimationExpiration]] = {
    val findQuery: F[Option[EstimationExpiration]] =
      sql"""
         SELECT 
           estimation_close_at
         FROM estimation_expiration
         WHERE quest_id = $questId
       """.query[EstimationExpiration].option.transact(transactor)

    findQuery
  }

  override def getExpiredQuestIds(now: Instant): F[List[ExpiredQuests]] = {

    val gracePeriod = java.time.Duration.ofMinutes(30)
    val upperBound = now.plus(gracePeriod)

    val findQuery: F[List[ExpiredQuests]] =
      sql"""
         SELECT 
           quest_id
         FROM estimation_expiration
         WHERE estimation_close_at <= $upperBound
       """.query[ExpiredQuests].to[List].transact(transactor)

    findQuery
  }

  override def getExpirations(questId: String, limit: Int, offset: Int): F[List[EstimationExpiration]] = {
    val query =
      sql"""
        SELECT 
          estimation_close_at
        FROM estimation_expiration
        WHERE quest_id = $questId 
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[EstimationExpiration]
        .to[List]

    for {
      _ <- Logger[F].debug(s"[EstimationExpirationRepositoryImpl][getDevBids] Fetching estimation closing time: questId=$questId, limit=$limit, offset=$offset")
      result <- query.transact(transactor)
      _ <- result.traverse_(bid => Logger[F].debug(s"[EstimationExpirationRepositoryImpl][getDevBids] Fetched bid: ${bid}"))
    } yield result
  }

  override def upsertEstimationCloseAt(
    questId: String,
    clientId: String,
    closeAt: Instant
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO estimation_expiration (quest_id, client_id, estimation_close_at)
      VALUES ($questId, $clientId, $closeAt)
      ON CONFLICT (quest_id)
      DO UPDATE SET 
        estimation_close_at = EXCLUDED.estimation_close_at,
        updated_at = CURRENT_TIMESTAMP
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(_) =>
          UpdateSuccess.validNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
          ForeignKeyViolationError.invalidNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
          DatabaseConnectionError.invalidNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "22001" =>
          DataTooLongError.invalidNel
        case Left(ex: java.sql.SQLException) =>
          SqlExecutionError(ex.getMessage).invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
      }

  override def upsertExpirations(
    clientId: String,
    questId: String,
    expiration: EstimationExpiration
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
    INSERT INTO estimation_expiration (
      quest_id,
      client_id,
      estimation_close_at
    ) VALUES (
      $questId,
      $clientId,
      ${expiration.estimationCloseAt}
    )
    ON CONFLICT (client_id)
    DO UPDATE SET 
      client_id = EXCLUDED.client_id,
      estimation_close_at = EXCLUDED.estimation_close_at,
      updated_at = CURRENT_TIMESTAMP
  """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          CreateSuccess.validNel
        case Left(e: java.sql.SQLIntegrityConstraintViolationException) =>
          ConstraintViolation.invalidNel
        case Left(e: java.sql.SQLException) =>
          DatabaseConnectionError.invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

}

object EstimationExpirationRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): EstimationExpirationRepositoryAlgebra[F] =
    new EstimationExpirationRepositoryImpl[F](transactor)
}
