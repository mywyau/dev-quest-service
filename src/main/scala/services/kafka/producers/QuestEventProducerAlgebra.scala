package services.kafka.producers

import cats.effect.Sync
import cats.syntax.all.*
import fs2.kafka.*
import io.circe.syntax.*
import models.events.QuestCreatedEvent
import models.kafka.*

trait QuestEventProducerAlgebra[F[_]] {

  def publishQuestCreated(event: QuestCreatedEvent): F[KafkaProducerResult]
}

final class QuestEventProducerImpl[F[_] : Sync](topic: String, producer: KafkaProducer[F, String, String]) extends QuestEventProducerAlgebra[F] {

  override def publishQuestCreated(event: QuestCreatedEvent): F[KafkaProducerResult] = {
    val record = ProducerRecord(topic, event.questId, event.asJson.noSpaces)
    val records = ProducerRecords.one(record)

    producer.produce(records).flatten.attempt.map {
      case Right(_) => SuccessfulWrite
      case Left(e) => FailedWrite(e.getMessage)
    }
  }

}
