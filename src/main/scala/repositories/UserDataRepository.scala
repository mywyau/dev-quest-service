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
import models.UserType
import models.database.*
import models.users.*
import org.typelevel.log4cats.Logger
import utils.DatabaseErrorHandler
import utils.DatabaseErrorHandler.*

import java.sql.Timestamp
import java.time.LocalDateTime

trait UserDataRepositoryAlgebra[F[_]] {

  def findUser(userId: String): F[Option[UserData]]

  def findUserNoUserName(userId: String): F[Option[RegistrationUserDataPartial]]

  def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateUserData(userId: String, updateUserData: UpdateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def registerUser(userId: String, userType: Registration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteUser(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class UserDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends UserDataRepositoryAlgebra[F] {

  implicit val userMeta: Meta[UserType] = 
    Meta[String].timap(UserType.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def findUser(userId: String): F[Option[UserData]] = {
    val findQuery: F[Option[UserData]] =
      sql"""
         SELECT 
            user_id,
            username,
            email,
            first_name,
            last_name,
            user_type
         FROM users
         WHERE user_id = $userId
       """.query[UserData].option.transact(transactor)

    findQuery
  }

  override def findUserNoUserName(userId: String): F[Option[RegistrationUserDataPartial]] = {
    val findQuery: F[Option[RegistrationUserDataPartial]] =
      sql"""
         SELECT 
            user_id,
            email,
            first_name,
            last_name,
            user_type
         FROM users
         WHERE user_id = $userId
       """.query[RegistrationUserDataPartial].option.transact(transactor)

    findQuery
  }

  override def createUser(
    userId: String,
    createUserData: CreateUserData
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO users (
        user_id,
        email,
        first_name,
        last_name,
        user_type
      )
      VALUES (
        $userId,
        ${createUserData.email},
        ${createUserData.firstName},
        ${createUserData.lastName},
        ${createUserData.userType}
      )
      ON CONFLICT (user_id) DO UPDATE
      SET
        email = EXCLUDED.email,
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        user_type = EXCLUDED.user_type,
        updated_at = CURRENT_TIMESTAMP
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(_) =>
          UpdateSuccess.validNel
        case Left(e: java.sql.SQLException) =>
          DatabaseConnectionError.invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
      }

  override def updateUserData(userId: String, updateUserData: UpdateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE users
      SET
          first_name = ${updateUserData.firstName},
          last_name = ${updateUserData.lastName},
          email = ${updateUserData.email},
          user_type = ${updateUserData.userType}
      WHERE user_id = ${userId}
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

  override def registerUser(userId: String, userType: Registration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE users
      SET
          username = ${userType.username},
          first_name = ${userType.firstName},
          last_name = ${userType.lastName},
          user_type = ${userType.userType}
      WHERE user_id = $userId
    """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(1) => UpdateSuccess.validNel
        case Right(0) => NotFoundError.invalidNel
        case Right(_) => UnexpectedResultError.invalidNel
        case Left(ex) => DatabaseErrorHandler.fromThrowable(ex).invalidNel
      }

  override def deleteUser(user_id: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val deleteQuery: Update0 =
      sql"""
        DELETE FROM users
        WHERE user_id = $user_id
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

object UserDataRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): UserDataRepositoryAlgebra[F] =
    new UserDataRepositoryImpl[F](transactor)
}
