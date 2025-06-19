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
import models.skills.Questing
import models.skills.Reviewing
import models.skills.Skill
import models.skills.SkillData
import models.skills.Testing
import models.users.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.LocalDateTime

trait SkillDataRepositoryAlgebra[F[_]] {

  def getSkillData(devId: String, skill: Skill): F[Option[SkillData]]

  def getHiscoreSkillData(skill: Skill): F[List[SkillData]]

  def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

}

class SkillDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends SkillDataRepositoryAlgebra[F] {

  implicit val skillMeta: Meta[Skill] = Meta[String].timap(Skill.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getSkillData(devId: String, skill: Skill): F[Option[SkillData]] = {
    val findQuery: F[Option[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          skill,
          level,
          xp
        FROM skill
        WHERE dev_id = $devId AND skill = $skill
      """
        .query[SkillData]
        .option
        .transact(transactor)

    findQuery
  }

  override def getHiscoreSkillData(skill: Skill): F[List[SkillData]] = {
    val findQuery: F[List[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          skill,
          level,
          xp
        FROM skill
        WHERE skill = $skill
      """
        .query[SkillData]
        .to[List]
        .transact(transactor)

    findQuery
  }

  override def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO skill (dev_id, username, skill, xp, level)
      VALUES ($devId, $username, ${skill.toString()}, $xp, LEAST(99, FLOOR(SQRT($xp) / 10)::int + 1) )
      ON CONFLICT (dev_id, skill)
      DO UPDATE SET xp = skill.xp + $xp,
      level = LEAST(99, FLOOR(SQRT(skill.xp + $xp) / 10)::int + 1)
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

object SkillDataRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): SkillDataRepositoryAlgebra[F] =
    new SkillDataRepositoryImpl[F](transactor)
}
