package repositories

import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import cats.Monad
import configuration.AppConfig
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.implicits.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import models.database.*
import models.languages.Language
import models.quests.*
import models.skills.Skill
import models.work_time.*
import models.Estimated
import models.NotEstimated
import models.NotStarted
import models.Open
import models.PaidOut
import models.QuestStatus
import models.Rank
import org.typelevel.log4cats.Logger

trait QuestRepositoryAlgebra[F[_]] {

  def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteAllByUserId(clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def create(request: CreateQuest): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def countNotEstimatedAndOpenQuests(): F[Int]

  def countActiveQuests(devId: String): F[Int]

  def findAllByUserId(clientId: String): F[List[QuestPartial]]

  def findByQuestId(questId: String): F[Option[QuestPartial]]

  def findQuestsWithExpiredEstimation(now: Instant): F[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]]

  def markPaid(questId: String): F[Unit]

  def setEstimationCloseAt(questId: String, closeAt: Instant): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def setFinalRank(questId: String, rank: Rank): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def validateOwnership(questId: String, clientId: String): F[Unit]

  def createHoursOfWork(questId: String, request: HoursOfWork): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends QuestRepositoryAlgebra[F] {

  implicit val questMeta: Meta[QuestStatus] = Meta[String].timap(QuestStatus.fromString)(_.toString)

  implicit val rank: Meta[Rank] = Meta[String].timap(Rank.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  implicit val metaStringList: Meta[Seq[String]] = Meta[Array[String]].imap(_.toSeq)(_.toArray)

  override def setFinalRank(questId: String, rank: Rank): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE quests
      SET rank = $rank,
          estimated = ${true},
          updated_at = NOW()
      WHERE quest_id = $questId
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          UpdateSuccess.validNel
        case Right(affectedRows) if affectedRows == 0 =>
          NotFoundError.invalidNel
        case Left(e: java.sql.SQLIntegrityConstraintViolationException) =>
          ConstraintViolation.invalidNel
        case Left(e: java.sql.SQLException) =>
          DatabaseConnectionError.invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  override def countNotEstimatedAndOpenQuests(): F[Int] =
    sql"""
          SELECT COUNT(*) FROM quests
          WHERE status IN ('NotEstimated', 'Open', 'Estimated')
        """.query[Int].unique.transact(transactor)

  override def countActiveQuests(devId: String): F[Int] =
    sql"""
          SELECT COUNT(*) FROM quests
          WHERE dev_id = $devId AND status IN ('NotStarted', 'InProgress', 'Review')
        """.query[Int].unique.transact(transactor)

  override def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE status = $questStatus 
          AND client_id = $clientId  
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository][streamByQuestStatus] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE status = $questStatus 
          AND dev_id = $devId  
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository][streamByQuestStatusDev] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE client_id = $clientId
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository] Streaming quests (clientId=$clientId, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE status IN (${NotEstimated.toString()}, ${Open.toString()}, ${Estimated.toString()})
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository] Streaming quests (limit=$limit, offset=$offset)")) >> queryStream
  }

  override def findAllByUserId(clientId: String): F[List[QuestPartial]] = {
    val findQuery: F[List[QuestPartial]] =
      sql"""
         SELECT 
           quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
         FROM quests
         WHERE client_id = $clientId
       """.query[QuestPartial].to[List].transact(transactor)

    findQuery
  }

  override def findByQuestId(questId: String): F[Option[QuestPartial]] = {
    val findQuery: F[Option[QuestPartial]] =
      sql"""
         SELECT 
           quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
         FROM quests
         WHERE quest_id = $questId
       """.query[QuestPartial].option.transact(transactor)

    findQuery
  }

  override def create(request: CreateQuest): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val tagArray: Array[String] = request.tags.map(_.toString).toArray
    sql"""
      INSERT INTO quests (
         quest_id, client_id, rank, title, description, acceptance_criteria, status, tags
      )
      VALUES (
        ${request.questId},
        ${request.clientId},
        ${request.rank},
        ${request.title},
        ${request.description},
        ${request.acceptanceCriteria},
        ${request.status},
        $tagArray
      )
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

  override def update(quest_id: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE quests
      SET
          title = ${request.title},
          description = ${request.description},
          acceptance_criteria = ${request.acceptanceCriteria},
          updated_at = ${LocalDateTime.now()}
      WHERE quest_id = ${quest_id}
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          UpdateSuccess.validNel
        case Right(affectedRows) if affectedRows == 0 =>
          NotFoundError.invalidNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
          ForeignKeyViolationError.invalidNel // Foreign key constraint violation
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
          DatabaseConnectionError.invalidNel // Database connection issue
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "22001" =>
          DataTooLongError.invalidNel // Data length exceeds column limit
        case Left(ex: java.sql.SQLException) =>
          SqlExecutionError(ex.getMessage).invalidNel // General SQL execution error
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  override def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE quests
      SET
          status = ${questStatus},
          updated_at = NOW()
      WHERE quest_id = ${questId}
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          UpdateSuccess.validNel
        case Right(affectedRows) if affectedRows == 0 =>
          NotFoundError.invalidNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
          ForeignKeyViolationError.invalidNel // Foreign key constraint violation
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
          DatabaseConnectionError.invalidNel // Database connection issue
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "22001" =>
          DataTooLongError.invalidNel // Data length exceeds column limit
        case Left(ex: java.sql.SQLException) =>
          SqlExecutionError(ex.getMessage).invalidNel // General SQL execution error
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  override def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE quests
      SET
          dev_id = ${devId},
          status = ${NotStarted.toString()},
          updated_at = NOW()
      WHERE quest_id = ${questId}
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          UpdateSuccess.validNel
        case Right(affectedRows) if affectedRows == 0 =>
          NotFoundError.invalidNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
          ForeignKeyViolationError.invalidNel // Foreign key constraint violation
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
          DatabaseConnectionError.invalidNel // Database connection issue
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "22001" =>
          DataTooLongError.invalidNel // Data length exceeds column limit
        case Left(ex: java.sql.SQLException) =>
          SqlExecutionError(ex.getMessage).invalidNel // General SQL execution error
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  override def delete(quest_id: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val deleteQuery: Update0 =
      sql"""
        DELETE FROM quests
        WHERE quest_id = $quest_id
      """.update

    deleteQuery.run.transact(transactor).attempt.map {
      case Right(affectedRows) if affectedRows == 1 =>
        DeleteSuccess.validNel
      case Right(affectedRows) if affectedRows == 0 =>
        NotFoundError.invalidNel
      case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
        ForeignKeyViolationError.invalidNel
      case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
        DatabaseConnectionError.invalidNel
      case Left(ex: java.sql.SQLException) =>
        SqlExecutionError(ex.getMessage).invalidNel
      case Left(ex) =>
        UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
      case _ =>
        UnexpectedResultError.invalidNel
    }
  }

  override def deleteAllByUserId(clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val deleteQuery: Update0 =
      sql"""
         DELETE FROM quests
         WHERE client_id = $clientId
       """.update

    deleteQuery.run.transact(transactor).attempt.map {
      case Right(affectedRows) if affectedRows > 0 =>
        DeleteSuccess.validNel
      case Right(affectedRows) if affectedRows == 0 =>
        NotFoundError.invalidNel
      case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
        ForeignKeyViolationError.invalidNel
      case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
        DatabaseConnectionError.invalidNel
      case Left(ex: java.sql.SQLException) =>
        SqlExecutionError(ex.getMessage).invalidNel
      case Left(ex) =>
        UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
      case _ =>
        UnexpectedResultError.invalidNel
    }
  }

  override def validateOwnership(questId: String, clientId: String): F[Unit] =
    sql"""
      SELECT 1 FROM quests WHERE quest_id = $questId AND client_id = $clientId
    """.query[Int].option.transact(transactor).flatMap {
      case Some(_) => ().pure[F]
      case None => new Exception(s"Unauthorized: client [$clientId] does not own quest [$questId]").raiseError[F, Unit]
    }

  override def markPaid(questId: String): F[Unit] =
    sql"""
      UPDATE quests
      SET status = ${PaidOut.toString}, updated_at = NOW()
      WHERE quest_id = $questId
    """.update.run.transact(transactor).flatMap {
      case 1 => ().pure[F]
      case _ => new Exception(s"Failed to mark quest [$questId] as paid. Quest not found or update failed.").raiseError[F, Unit]
    }

  override def setEstimationCloseAt(questId: String, closeAt: Instant): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
        UPDATE quests
        SET estimation_close_at = $closeAt
        WHERE quest_id = $questId;
      """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          UpdateSuccess.validNel
        case Right(affectedRows) if affectedRows == 0 =>
          NotFoundError.invalidNel
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "23503" =>
          ForeignKeyViolationError.invalidNel // Foreign key constraint violation
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "08001" =>
          DatabaseConnectionError.invalidNel // Database connection issue
        case Left(ex: java.sql.SQLException) if ex.getSQLState == "22001" =>
          DataTooLongError.invalidNel // Data length exceeds column limit
        case Left(ex: java.sql.SQLException) =>
          SqlExecutionError(ex.getMessage).invalidNel // General SQL execution error
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  override def findQuestsWithExpiredEstimation(now: Instant): F[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]] = {

    val gracePeriod = java.time.Duration.ofMinutes(30)
    val upperBound = now.plus(gracePeriod)

    sql"""
      SELECT 
         quest_id, client_id, dev_id, rank, title, hours_of_work, description, acceptance_criteria, status, tags, estimation_close_at, estimated
      FROM quests
      WHERE estimation_close_at IS NOT NULL
        AND estimation_close_at <= $upperBound
        AND status = 'NotEstimated'
    """
      .query[QuestPartial]
      .to[List]
      .transact(transactor)
      .attempt
      .map {
        case Right(retrievedRows) =>
          ReadSuccess[List[QuestPartial]](retrievedRows).validNel
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
  }

  override def createHoursOfWork(questId: String, request: HoursOfWork): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO quests (
        quest_id,
        hours_of_work
      ) VALUES (
        ${questId},
        ${request.hoursOfWork}
      ) ON CONFLICT (quest_id, hours_of_work) 
      DO UPDATE SET 
        quest_id = ${questId},
        hours_of_work = ${request.hoursOfWork}
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

object QuestRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): QuestRepositoryAlgebra[F] =
    new QuestRepositoryImpl[F](transactor)
}
