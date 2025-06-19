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
import models.languages.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.LocalDateTime

trait LanguageRepositoryAlgebra[F[_]] {

  def getLanguage(devId: String, language: Language): F[Option[LanguageData]]

  def getHiscoreLanguageData(language: Language): F[List[LanguageData]]

  def awardLanguageXP(devId: String, username: String, language: String, xp: BigDecimal): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class LanguageRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends LanguageRepositoryAlgebra[F] {

  implicit val languageMeta: Meta[Language] = Meta[String].timap(Language.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getLanguage(devId: String, language: Language): F[Option[LanguageData]] = {
    val findQuery: F[Option[LanguageData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          language,
          level,
          xp
        FROM langauges
        WHERE dev_id = $devId AND language = $language
      """
        .query[LanguageData]
        .option
        .transact(transactor)

    findQuery
  }

  override def getHiscoreLanguageData(language: Language): F[List[LanguageData]] = {
    val findQuery: F[List[LanguageData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          language,
          level,
          xp
        FROM language
        WHERE language = $language
      """
        .query[LanguageData]
        .to[List]
        .transact(transactor)

    findQuery
  }

  override def awardLanguageXP(devId: String, username: String, language: String, xp: BigDecimal): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
     //TODO: calcs the level here this needs to change we want to calc the level on upsert in backend 
     //       VALUES ($devId, $username, $language, $xp, 1)
     //      level = LEAST(99, FLOOR(SQRT(language.xp + $xp) / 10)::int + 1) 
    sql"""
      INSERT INTO language (dev_id, username, language, xp, level)
      VALUES ($devId, $username, $language, $xp, LEAST(99, FLOOR(SQRT($xp) / 10)::int + 1) )
      ON CONFLICT (dev_id, language)
      DO UPDATE SET xp = language.xp + $xp,
      level = LEAST(99, FLOOR(SQRT(language.xp + $xp) / 10)::int + 1) 
    """.update.run.transact(transactor).attempt.map {
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
}

object LanguageRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): LanguageRepositoryAlgebra[F] =
    new LanguageRepositoryImpl[F](transactor)
}
