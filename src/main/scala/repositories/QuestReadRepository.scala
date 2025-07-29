package repositories

import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import cats.Monad
import configuration.AppConfig
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.implicits.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import models.database.*
import models.languages.Language
import models.quests.*
import models.skills.Skill
import models.Estimated
import models.NotEstimated
import models.NotStarted
import models.Open
import models.PaidOut
import models.QuestStatus
import models.Rank
import org.typelevel.log4cats.Logger

trait QuestReadRepositoryAlgebra[F[_]] {

  def countNotEstimatedAndOpenQuests(): F[Int]

  def countActiveQuests(devId: String): F[Int]

  def findAllByUserId(clientId: String): F[List[QuestPartial]]

  def findByQuestId(questId: String): F[Option[QuestPartial]]

  def findQuestsWithExpiredEstimation(now: Instant): F[List[QuestPartial]]
  
  def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial]

  def validateOwnership(questId: String, clientId: String): F[Unit]

}

class QuestReadRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends QuestReadRepositoryAlgebra[F] {

  implicit val questMeta: Meta[QuestStatus] = Meta[String].timap(QuestStatus.fromString)(_.toString)

  implicit val rank: Meta[Rank] = Meta[String].timap(Rank.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  implicit val metaStringList: Meta[Seq[String]] = Meta[Array[String]].imap(_.toSeq)(_.toArray)

  override def countNotEstimatedAndOpenQuests(): F[Int] =
    sql"""
          SELECT COUNT(*) FROM quests
          WHERE status IN ('NotEstimated', 'Open', 'Estimated')
        """.query[Int].unique.transact(transactor)

  override def countActiveQuests(devId: String): F[Int] =
    sql"""
          SELECT COUNT(*) FROM quests
          WHERE dev_id = $devId AND status IN ('NotStarted', 'InProgress', 'Review')
        """.query[Int].unique.transact(transactor)

  override def findAllByUserId(clientId: String): F[List[QuestPartial]] = {
    val findQuery: F[List[QuestPartial]] =
      sql"""
         SELECT 
           quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
         FROM quests
         WHERE client_id = $clientId
       """.query[QuestPartial].to[List].transact(transactor)

    findQuery
  }

  override def findByQuestId(questId: String): F[Option[QuestPartial]] = {
    val findQuery: F[Option[QuestPartial]] =
      sql"""
         SELECT 
           quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
         FROM quests
         WHERE quest_id = $questId
       """.query[QuestPartial].option.transact(transactor)

    findQuery
  }

  override def findQuestsWithExpiredEstimation(now: Instant): F[List[QuestPartial]] =
    sql"""
        SELECT 
           quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE estimation_close_at IS NOT NULL
          AND estimation_close_at <= $now
          AND status = 'NotEstimated'
      """
      .query[QuestPartial]
      .to[List]
      .transact(transactor)

  override def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE status IN (${NotEstimated.toString()}, ${Open.toString()}, ${Estimated.toString()})
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository] Streaming quests (limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE status = $questStatus 
          AND client_id = $clientId  
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository][streamByQuestStatus] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE status = $questStatus 
          AND dev_id = $devId  
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository][streamByQuestStatusDev] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial] = {
    val queryStream: Stream[F, QuestPartial] =
      sql"""
        SELECT quest_id, client_id, dev_id, rank, title, description, acceptance_criteria, status, tags, estimation_close_at, estimated
        FROM quests
        WHERE client_id = $clientId
        ORDER BY created_at DESC
        LIMIT $limit OFFSET $offset
      """
        .query[QuestPartial]
        .stream
        .transact(transactor)
        .evalTap(q => Logger[F].debug(s"[QuestRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestRepository] Streaming quests (clientId=$clientId, limit=$limit, offset=$offset)")) >> queryStream
  }

  override def validateOwnership(questId: String, clientId: String): F[Unit] =
    sql"""
      SELECT 1 FROM quests WHERE quest_id = $questId AND client_id = $clientId
    """.query[Int].option.transact(transactor).flatMap {
      case Some(_) => ().pure[F]
      case None => new Exception(s"Unauthorized: client [$clientId] does not own quest [$questId]").raiseError[F, Unit]
    }

}

object QuestReadRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): QuestReadRepositoryAlgebra[F] =
    new QuestReadRepositoryImpl[F](transactor)
}
