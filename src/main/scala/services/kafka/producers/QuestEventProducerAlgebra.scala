package services.kafka.producers

import cats.effect.Sync
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import fs2.kafka.*
import io.circe.syntax.*
import models.events.QuestCreatedEvent

trait QuestEventProducerAlgebra[F[_]] {

  def publishQuestCreated(event: QuestCreatedEvent): F[Unit]
}

final class QuestEventProducerImpl[F[_] : Sync](
  topic: String,
  producer: KafkaProducer[F, String, String]
) extends QuestEventProducerAlgebra[F] {

  override def publishQuestCreated(event: QuestCreatedEvent): F[Unit] = {
    val record = ProducerRecord(topic, event.questId, event.asJson.noSpaces)
    val records = ProducerRecords.one(record)

    // produce returns F[F[RecordMetadata]]; flatten + ignore result
    producer.produce(records).flatten.void
  }
}
