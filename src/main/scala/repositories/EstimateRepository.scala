package repositories

import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import cats.Monad
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.implicits.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import java.sql.Timestamp
import java.time.LocalDateTime
import models.*
import models.database.*
import models.estimate.*
import models.languages.Language
import models.skills.Skill
import models.NotStarted
import models.Open
import models.Rank
import org.typelevel.log4cats.Logger

trait EstimateRepositoryAlgebra[F[_]] {

  def countEstimates(devId: String): F[Int]

  def getEstimates(questId: String): F[List[Estimate]]

  def createEstimation(estimateId: String, devId: String, username: String, estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class EstimateRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends EstimateRepositoryAlgebra[F] {

  implicit val rank: Meta[Rank] = Meta[String].timap(Rank.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  implicit val metaStringList: Meta[Seq[String]] = Meta[Array[String]].imap(_.toSeq)(_.toArray)

  override def countEstimates(devId: String): F[Int] =
    sql"""
        SELECT COUNT(*) 
        FROM quest_estimations 
        WHERE dev_id = $devId
      """.query[Int].unique.transact(transactor)

  override def getEstimates(questId: String): F[List[Estimate]] = {
    val findQuery: F[List[Estimate]] =
      sql"""
         SELECT 
          dev_id, username, score, estimated_days, comment
         FROM quest_estimations
         WHERE quest_id = $questId
       """.query[Estimate].to[List].transact(transactor)

    findQuery
  }

  override def createEstimation(
    estimateId: String,
    devId: String,
    username: String,
    estimate: CreateEstimate
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val query =
      sql"""
        INSERT INTO quest_estimations (estimate_id, dev_id, quest_id, username, score, estimated_days, comment)
        VALUES ($estimateId, $devId, ${estimate.questId}, ${username}, ${estimate.score}, ${estimate.days}, ${estimate.comment})
        ON CONFLICT (quest_id, dev_id) DO NOTHING
      """.update.run

    query.transact(transactor).attempt.map {
      case Right(affectedRows) if affectedRows >= 1 =>
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
  }
}

object EstimateRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): EstimateRepositoryAlgebra[F] =
    new EstimateRepositoryImpl[F](transactor)
}
