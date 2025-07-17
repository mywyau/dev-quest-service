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
import models.skills.*
import models.users.*
import org.typelevel.log4cats.Logger
import services.*

import java.sql.Timestamp
import java.time.LocalDateTime

trait SkillDataRepositoryAlgebra[F[_]] {

  def getAllSkillData(): F[List[SkillData]]

  def getAllSkills(devId: String): F[List[SkillData]]

  def getSkillsForUser(username: String): F[List[SkillData]]

  def getSkill(devId: String, skill: Skill): F[Option[SkillData]]

  def getHiscoreSkillData(skill: Skill): F[List[SkillData]]

  def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal, level: Int): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

}

class SkillDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](
  transactor: Transactor[F]
) extends SkillDataRepositoryAlgebra[F] {

  implicit val skillMeta: Meta[Skill] = Meta[String].timap(Skill.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getAllSkillData(): F[List[SkillData]] = {
    val findQuery: F[List[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          skill,
          level,
          xp
        FROM skill
      """
        .query[SkillData]
        .to[List]
        .transact(transactor)

    findQuery
  }

  override def getSkillsForUser(username: String): F[List[SkillData]] = {
    val findQuery: F[List[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          skill,
          level,
          xp
        FROM skill
        WHERE username = $username
      """
        .query[SkillData]
        .to[List]
        .transact(transactor)

    findQuery
  }

  override def getAllSkills(devId: String): F[List[SkillData]] = {
    val findQuery: F[List[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          username,
          skill,
          level,
          xp
        FROM skill
        WHERE dev_id = $devId
      """
        .query[SkillData]
        .to[List]
        .transact(transactor)

    findQuery
  }

  override def getSkill(devId: String, skill: Skill): F[Option[SkillData]] = {
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

  override def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal, level: Int): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val query =
      sql"""
      INSERT INTO skill (dev_id, username, skill, xp, level)
      VALUES ($devId, $username, ${skill.toString}, $xp, $level)
      ON CONFLICT (dev_id, skill)
      DO UPDATE SET
        xp = skill.xp + $xp,
        level = $level
    """.update.run

    query.transact(transactor).attempt.map {
      case Right(affectedRows) if affectedRows == 1 =>
        UpdateSuccess.validNel
      case Right(0) =>
        NotFoundError.invalidNel
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
      case _ =>
        UnexpectedResultError.invalidNel
    }
  }

}

object SkillDataRepository {
  def apply[F[_] : Concurrent : Monad : Logger](
    transactor: Transactor[F]
  ): SkillDataRepositoryAlgebra[F] =
    new SkillDataRepositoryImpl[F](
      transactor
    )
}
