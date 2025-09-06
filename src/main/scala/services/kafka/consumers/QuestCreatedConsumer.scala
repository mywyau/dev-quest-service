// src/main/scala/kafka/consumers/QuestCreatedConsumer.scala

package services.kafka.consumers

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.kafka.*
import io.circe.parser.decode
import models.events.QuestCreatedEvent
import org.typelevel.log4cats.Logger

object QuestCreatedConsumer {

  final case class Settings(
    bootstrapServers: String,
    groupId: String = "dev-quest-consumers",
    topic: String = "quest.created.v1"
  )

  def resource[F[_]: Async: Logger](
    cfg: Settings
  ): Resource[F, fs2.Stream[F, Unit]] = {
    val consumerSettings =
      ConsumerSettings[F, String, String]
        .withBootstrapServers(cfg.bootstrapServers)
        .withGroupId(cfg.groupId)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        // good default; let us control commits after successful processing
        .withEnableAutoCommit(false)

    Resource.pure {
      KafkaConsumer
        .stream(consumerSettings)
        .subscribeTo(cfg.topic)
        .records
        .evalMap { committable =>
          val record = committable.record
          val key    = Option(record.key).getOrElse("null")

          // decode JSON payload
          decode[QuestCreatedEvent](record.value) match {
            case Right(ev) =>
              Logger[F].info(
                s"[QuestCreatedConsumer] key=$key partition=${record.partition} offset=${record.offset} questId=${ev.questId} title=${ev.title}"
              ) *>
              // TODO: call your Notification/Email/Projection service here
              committable.offset.commit

            case Left(err) =>
              Logger[F].warn(
                s"[QuestCreatedConsumer] JSON decode failed at partition=${record.partition} offset=${record.offset}: ${err.getMessage}; value='${record.value.take(300)}'"
              ) *>
              // choose: commit (skip) or don't commit (will retry); for first run, skip to avoid poison pill loops
              committable.offset.commit
          }
        }
    }
  }
}
