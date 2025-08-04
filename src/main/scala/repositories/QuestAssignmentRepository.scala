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
import models.dev_bids.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime

trait QuestAssignmentRepositoryAlgebra[F[_]] {

  def getBidCount(questId: String): F[Int]

  def getBid(questId: String): F[Option[GetDevBid]]

  def getDevBids(questId: String, limit: Int, offset: Int): F[List[GetDevBid]]

  def upsertBid(devId: String, questId: String, username: String, devBid: DevBid): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestAssignmentRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends QuestAssignmentRepositoryAlgebra[F] {

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getBidCount(questId: String): F[Int] = {
    val findQuery: F[Int] =
      sql"""
        SELECT COUNT(*) 
        FROM dev_bids 
        WHERE quest_id = $questId
      """.query[Int].unique.transact(transactor)

    findQuery
  }

  override def getBid(questId: String): F[Option[GetDevBid]] = {
    val findQuery: F[Option[GetDevBid]] =
      sql"""
         SELECT 
           dev_username,
           bid
         FROM dev_bids
         WHERE quest_id = $questId
       """.query[GetDevBid].option.transact(transactor)

    findQuery
  }

  override def getDevBids(questId: String, limit: Int, offset: Int): F[List[GetDevBid]] = {
    val query =
      sql"""
        SELECT 
          dev_username,
          bid
        FROM dev_bids
        WHERE quest_id = $questId 
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[GetDevBid]
        .to[List]

    for {
      _ <- Logger[F].debug(s"[QuestAssignmentRepositoryImpl][getDevBids] Fetching dev bids: questId=$questId, limit=$limit, offset=$offset")
      result <- query.transact(transactor)
      _ <- result.traverse_(bid => Logger[F].debug(s"[QuestAssignmentRepositoryImpl][getDevBids] Fetched bid: ${bid}"))
    } yield result
  }

  override def upsertBid(
    devId: String,
    questId: String,
    devUsername: String,
    devBid: DevBid
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
    INSERT INTO dev_bids (
      quest_id,
      dev_id,
      dev_username,
      bid
    ) VALUES (
      $questId,
      $devId,
      ${devUsername},
      ${devBid.bid}
    )
    ON CONFLICT (dev_id)
    DO UPDATE SET 
      dev_id = EXCLUDED.dev_id,
      dev_username = EXCLUDED.dev_username,
      bid = EXCLUDED.bid,
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

object QuestAssignmentRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): QuestAssignmentRepositoryAlgebra[F] =
    new QuestAssignmentRepositoryImpl[F](transactor)
}
