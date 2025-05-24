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
import models.database.DatabaseError
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.database.DeleteSuccess
import models.database.ForeignKeyViolationError
import models.database.NotFoundError
import models.database.SqlExecutionError
import models.database.UnexpectedResultError
import models.database.UnknownError
import models.database.UpdateSuccess
import models.users.*
import models.UserType
import org.typelevel.log4cats.Logger

trait UserDataRepositoryAlgebra[F[_]] {

  def findUser(userId: String): F[Option[UserData]]

  def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateUserType(userId: String, userType: UserType): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteUser(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class UserDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends UserDataRepositoryAlgebra[F] {

  implicit val userMeta: Meta[UserType] = Meta[String].timap(UserType.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def findUser(userId: String): F[Option[UserData]] = {
    val findQuery: F[Option[UserData]] =
      sql"""
         SELECT 
            user_id,
            email,
            first_name,
            last_name,
            user_type
         FROM users
         WHERE user_id = $userId
       """.query[UserData].option.transact(transactor)

    findQuery
  }

  override def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO users (
        user_id,
        email,
        first_name,
        last_name,
        user_type
      )
      VALUES (
        ${userId},
        ${createUserData.email},
        ${createUserData.firstName},
        ${createUserData.lastName},
        ${createUserData.userType}
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

  override def updateUserType(userId: String, userType: UserType): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      UPDATE users
      SET
          user_type = ${userType}
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
