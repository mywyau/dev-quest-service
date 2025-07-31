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

trait QuestStreamingRepositoryAlgebra[F[_]] {

  def streamAll(limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatus(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[F, QuestPartial]

  def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[F, QuestPartial]
}

class QuestStreamingRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends QuestStreamingRepositoryAlgebra[F] {

  implicit val questMeta: Meta[QuestStatus] = Meta[String].timap(QuestStatus.fromString)(_.toString)

  implicit val rank: Meta[Rank] = Meta[String].timap(Rank.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  implicit val metaStringList: Meta[Seq[String]] = Meta[Array[String]].imap(_.toSeq)(_.toArray)

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
        .evalTap(q => Logger[F].debug(s"[QuestStreamingRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestStreamingRepository][streamByQuestStatus] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
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
        .evalTap(q => Logger[F].debug(s"[QuestStreamingRepository][streamByQuestStatus] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestStreamingRepository][streamByQuestStatusDev] Streaming quests (questStatus=$questStatus, limit=$limit, offset=$offset)")) >> queryStream
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
        .evalTap(q => Logger[F].debug(s"[QuestStreamingRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestStreamingRepository] Streaming quests (clientId=$clientId, limit=$limit, offset=$offset)")) >> queryStream
  }

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
        .evalTap(q => Logger[F].debug(s"[QuestStreamingRepository] Fetched quest: ${q.questId}"))

    Stream.eval(Logger[F].debug(s"[QuestStreamingRepository] Streaming quests (limit=$limit, offset=$offset)")) >> queryStream
  }

}

object QuestStreamingRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): QuestStreamingRepositoryAlgebra[F] =
    new QuestStreamingRepositoryImpl[F](transactor)
}
