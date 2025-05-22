package repositories

import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import cats.Monad
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import java.sql.Timestamp
import java.time.LocalDateTime
import models.database.*
import models.quests.*
import models.QuestStatus
import org.typelevel.log4cats.Logger

trait QuestRepositoryAlgebra[F[_]] {

  def streamByUserId(userId: String, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial]

  def findAllByUserId(userId: String): F[List[QuestPartial]]

  def findByQuestId(questId: String): F[Option[QuestPartial]]

  def create(request: CreateQuest): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, request: UpdateQuestPartial): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteAllByUserId(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class QuestRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends QuestRepositoryAlgebra[F] {

  implicit val questMeta: Meta[QuestStatus] = Meta[String].timap(QuestStatus.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def streamByUserId(userId: String, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT user_id, quest_id, title, description, status
        FROM quests
        WHERE user_id = $userId
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].info(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].info(s"[QuestRepository] Streaming quests (userId=$userId, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT user_id, quest_id, title, description, status
        FROM quests
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].info(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].info(s"[QuestRepository] Streaming quests (limit=$limit, offset=$offset)")) >> queryStream
  }

  override def findAllByUserId(userId: String): F[List[QuestPartial]] = {
    val findQuery: F[List[QuestPartial]] =
      sql"""
         SELECT 
            user_id,
            quest_id,
            title,
            description,
            status
         FROM quests
         WHERE user_id = $userId
       """.query[QuestPartial].to[List].transact(transactor)

    findQuery
  }

  override def findByQuestId(questId: String): F[Option[QuestPartial]] = {
    val findQuery: F[Option[QuestPartial]] =
      sql"""
         SELECT 
            user_id,
            quest_id,
            title,
            description,
            status
         FROM quests
         WHERE quest_id = $questId
       """.query[QuestPartial].option.transact(transactor)

    findQuery
  }

  override def create(request: CreateQuest): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO quests (
         user_id,
         quest_id,
         title,
         description,
         status
      )
      VALUES (
        ${request.userId},
        ${request.questId},
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

  override def deleteAllByUserId(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val deleteQuery: Update0 =
      sql"""
         DELETE FROM quests
         WHERE user_id = $userId
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
