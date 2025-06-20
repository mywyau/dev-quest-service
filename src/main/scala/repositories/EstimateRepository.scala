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
import models.Assigned
import models.NotStarted
import models.Open
import models.Rank
import org.typelevel.log4cats.Logger

trait EstimateRepositoryAlgebra[F[_]] {

  def createEstimation(estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

}

class EstimateRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends EstimateRepositoryAlgebra[F] {

  // implicit val estimateMeta: Meta[EstimateStatus] = Meta[String].timap(EstimateStatus.fromString)(_.toString)

  implicit val rank: Meta[Rank] = Meta[String].timap(Rank.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  implicit val metaStringList: Meta[Seq[String]] = Meta[Array[String]].imap(_.toSeq)(_.toArray)

  override def createEstimation(estimate: CreateEstimate): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {
    val query =
      sql"""
        INSERT INTO quest_estimations (quest_id, dev_id, comments, rank_vote)
        VALUES (${estimate.questId}, ${estimate.devId}, ${estimate.comments}, ${estimate.rankVote})
        ON CONFLICT (quest_id, dev_id)
        DO UPDATE SET 
          comments = EXCLUDED.comments, 
          rank = EXCLUDED.rank_vote, 
          created_at = CURRENT_TIMESTAMP
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
