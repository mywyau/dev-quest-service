package repositories

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.Assigned
import models.NotStarted
import models.QuestStatus
import models.database.*
import models.quests.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.LocalDateTime
import models.Open

trait QuestRepositoryAlgebra[F[_]] {

  def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial]

  def findAllByUserId(clientId: String): F[List[QuestPartial]]

  def findByQuestId(questId: String): F[Option[QuestPartial]]

  def create(request: CreateQuest): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateStatus(questId: String, questStatus: QuestStatus): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def acceptQuest(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteAllByUserId(clientId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends QuestRepositoryAlgebra[F] {

  implicit val questMeta: Meta[QuestStatus] = Meta[String].timap(QuestStatus.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, title, description, status
        FROM quests
        WHERE status = $questStatus 
          AND client_id = $clientId  
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].info(s"[QuestRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].info(s"[QuestRepository][streamByQuestStatus] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, title, description, status
        FROM quests
        WHERE status = $questStatus 
          AND dev_id = $devId  
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].info(s"[QuestRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].info(s"[QuestRepository][streamByQuestStatusDev] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, title, description, status
        FROM quests
        WHERE client_id = $clientId
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].info(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].info(s"[QuestRepository] Streaming quests (clientId=$clientId, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, title, description, status
        FROM quests
        WHERE status = ${Open.toString()}
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].info(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].info(s"[QuestRepository] Streaming quests (limit=$limit, offset=$offset)")) >> queryStream
  }

  override def findAllByUserId(clientId: String): F[List[QuestPartial]] = {
    val findQuery: F[List[QuestPartial]] =
      sql"""
         SELECT 
           quest_id, client_id, dev_id, title, description, status
         FROM quests
         WHERE client_id = $clientId
       """.query[QuestPartial].to[List].transact(transactor)

    findQuery
  }

  override def findByQuestId(questId: String): F[Option[QuestPartial]] = {
    val findQuery: F[Option[QuestPartial]] =
      sql"""
         SELECT 
           quest_id, client_id, dev_id, title, description, status
         FROM quests
         WHERE quest_id = $questId
       """.query[QuestPartial].option.transact(transactor)

    findQuery
  }

  override def create(request: CreateQuest): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO quests (
         quest_id, client_id, title, description, status
      )
      VALUES (
        ${request.questId},
        ${request.clientId},
        ${request.title},
        ${request.description},
        ${request.status}
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
          DatabaseError.invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  override def update(quest_id: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE quests
      SET
          title = ${request.title},
          description = ${request.description},
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
}

object QuestRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): QuestRepositoryAlgebra[F] =
    new QuestRepositoryImpl[F](transactor)
}
