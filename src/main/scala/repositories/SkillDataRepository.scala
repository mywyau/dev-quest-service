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
import models.Skill
import models.skills.SkillData
import org.typelevel.log4cats.Logger

trait SkillDataRepositoryAlgebra[F[_]] {

  def getTestingSkillData(skill: String): F[Option[SkillData]]
}

class SkillDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends SkillDataRepositoryAlgebra[F] {

  implicit val skillMeta: Meta[Skill] = Meta[String].timap(Skill.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def findUser(userId: String): F[Option[SkillData]] = {
    val findQuery: F[Option[SkillData]] =
      sql"""
         SELECT 
            dev_id,
            skill,
            level,
            xp,
         FROM users
         WHERE testing = $userId
       """.query[Skill].option.transact(transactor)

    findQuery
  }
}

object SkillDataRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): SkillDataRepositoryAlgebra[F] =
    new SkillDataRepositoryImpl[F](transactor)
}
