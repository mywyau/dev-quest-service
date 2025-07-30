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
import models.database.ConstraintViolation
import models.database.CreateSuccess
import models.database.DataTooLongError
import models.database.DatabaseConnectionError
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.database.DeleteSuccess
import models.database.ForeignKeyViolationError
import models.database.NotFoundError
import models.database.SqlExecutionError
import models.database.UnexpectedResultError
import models.database.UnknownError
import models.database.UpdateSuccess
import models.rewards.*
import models.RewardStatus
import org.typelevel.log4cats.Logger

trait RewardRepositoryAlgebra[F[_]] {

  def getRewardData(questId: String): F[Option[RewardData]]

  def streamRewardByQuest(questId: String): Stream[F, RewardData]

  def createCompletionReward(clientId: String, request: CreateCompletionReward): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def createTimeReward(clientId: String, request: CreateTimeReward): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def update(questId: String, updateRewardData: UpdateRewardData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateWithDevId(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class RewardRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends RewardRepositoryAlgebra[F] {

  implicit val rewardMeta: Meta[RewardStatus] = Meta[String].timap(RewardStatus.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getRewardData(questId: String): F[Option[RewardData]] = {
    val findQuery: F[Option[RewardData]] =
      sql"""
        SELECT
          quest_id,
          client_id,
          dev_id, 
          time_reward_value,
          completion_reward_value,
          paid
        FROM reward
        WHERE quest_id = $questId
       """.query[RewardData].option.transact(transactor)

    findQuery
  }

  override def streamRewardByQuest(questId: String): Stream[F, RewardData] =
    sql"""
      SELECT quest_id, client_id, dev_id, time_reward_value, completion_reward_value, paid
      FROM reward
      WHERE quest_id = $questId
      ORDER BY created_at DESC
    """
      .query[RewardData]
      .stream
      .transact(transactor)

  override def createCompletionReward(clientId: String, request: CreateCompletionReward): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO reward (
        quest_id,
        client_id,
        completion_reward_value
      )
      VALUES (
        ${request.questId},
        ${clientId},
        ${request.completionRewardValue}
      ) ON CONFLICT (quest_id, client_id) 
      DO UPDATE SET 
        quest_id = ${request.questId},
        client_id = ${clientId},
        completion_reward_value = ${request.completionRewardValue}
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

  override def createTimeReward(clientId: String, request: CreateTimeReward): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO reward (
        quest_id,
        client_id,
        time_reward_value
      )
      VALUES (
        ${request.questId},
        ${clientId},
        ${request.timeRewardValue}
      ) 
      ON CONFLICT (quest_id, client_id) 
      DO UPDATE SET 
        quest_id = ${request.questId},
        client_id = ${clientId},
        time_reward_value = ${request.timeRewardValue}
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

  override def updateWithDevId(questId: String, devId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE reward
      SET
          dev_id= ${devId},
          updated_at = ${LocalDateTime.now()}
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

  override def update(questId: String, updateRewardData: UpdateRewardData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE reward
      SET
          time_reward_value = ${updateRewardData.timeRewardValue},
          completion_reward_value = ${updateRewardData.completionRewardValue},
          updated_at = ${LocalDateTime.now()}
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

  override def delete(questId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val deleteQuery: Update0 =
      sql"""
        DELETE FROM reward
        WHERE quest_id = $questId
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

}

object RewardRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): RewardRepositoryAlgebra[F] =
    new RewardRepositoryImpl[F](transactor)
}
