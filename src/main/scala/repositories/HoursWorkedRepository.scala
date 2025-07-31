package repositories

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import configuration.AppConfig
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.implicits.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.Estimated
import models.NotEstimated
import models.NotStarted
import models.Open
import models.PaidOut
import models.QuestStatus
import models.Rank
import models.database.*
import models.languages.Language
import models.quests.*
import models.skills.Skill
import models.work_time.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime

trait HoursWorkedRepositoryAlgebra[F[_]] {

  def getHoursOfWork(questId: String): F[Option[HoursOfWork]]

  def upsertHoursOfWork(clientId: String, questId: String, request: HoursOfWork): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class HoursWorkedRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends HoursWorkedRepositoryAlgebra[F] {

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getHoursOfWork(questId: String): F[Option[HoursOfWork]] = {
    val findQuery: F[Option[HoursOfWork]] =
      sql"""
         SELECT 
           hours_of_work, 
         FROM quest_hours
         WHERE quest_id = $questId
       """.query[HoursOfWork].option.transact(transactor)

    findQuery
  }

  override def upsertHoursOfWork(clientId: String, questId: String, request: HoursOfWork): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
      INSERT INTO quest_hours (
        quest_id,
        client_id,
        hours_of_work
      ) VALUES (
        ${questId},
        ${clientId},
        ${request.hoursOfWork}
      ) ON CONFLICT (quest_id, hours_of_work) 
      DO UPDATE SET 
        quest_id = ${questId},
        client_id = ${clientId},
        hours_of_work = ${request.hoursOfWork}
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

}

object HoursWorkedRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): HoursWorkedRepositoryAlgebra[F] =
    new HoursWorkedRepositoryImpl[F](transactor)
}
