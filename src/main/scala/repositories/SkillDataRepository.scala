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
import models.skills.Skill
import models.skills.SkillData
import models.skills.Testing
import models.users.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.LocalDateTime
import models.skills.Reviewing

trait SkillDataRepositoryAlgebra[F[_]] {

  def getReviewingSkillData(devId: String): F[Option[SkillData]]
  
  def getQuestingSkillData(devId: String): F[Option[SkillData]]

  def getTestingSkillData(devId: String): F[Option[SkillData]]
}

class SkillDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends SkillDataRepositoryAlgebra[F] {

  implicit val skillMeta: Meta[Skill] = Meta[String].timap(Skill.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getReviewingSkillData(devId: String): F[Option[SkillData]] = {
    val findQuery: F[Option[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          level,
          xp
        FROM questing
        WHERE dev_id = $devId
      """.query[(String, Int, BigDecimal)]
        .option
        .map(_.map { case (devId, level, xp) =>
          SkillData(devId, Reviewing, level, xp)
        })
        .transact(transactor)


    findQuery
  }

  override def getQuestingSkillData(devId: String): F[Option[SkillData]] = {
    val findQuery: F[Option[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          level,
          xp
        FROM questing
        WHERE dev_id = $devId
      """.query[(String, Int, BigDecimal)]
        .option
        .map(_.map { case (devId, level, xp) =>
          SkillData(devId, Questing, level, xp)
        })
        .transact(transactor)


    findQuery
  }

  override def getTestingSkillData(devId: String): F[Option[SkillData]] = {
    val findQuery: F[Option[SkillData]] =
      sql"""
        SELECT 
          dev_id,
          level,
          xp
        FROM testing
        WHERE dev_id = $devId
      """.query[(String, Int, BigDecimal)]
        .option
        .map(_.map { case (devId, level, xp) =>
          SkillData(devId, Testing, level, xp)
        })
        .transact(transactor)

    findQuery
  }
}

object SkillDataRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): SkillDataRepositoryAlgebra[F] =
    new SkillDataRepositoryImpl[F](transactor)
}
