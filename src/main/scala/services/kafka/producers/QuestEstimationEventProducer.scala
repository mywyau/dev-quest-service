package services.kafka.producers

import cats.effect.Sync
import cats.syntax.all.*
import fs2.kafka.*
import io.circe.syntax.*
import models.kafka.QuestEstimationFinalized
import org.typelevel.log4cats.Logger
import models.kafka.*

trait QuestEstimationEventProducerAlgebra[F[_]] {
  
  def estimationFinalized(event: QuestEstimationFinalized): F[KafkaProducerResult]
}

class QuestEstimationEventProducerImpl[F[_] : Sync : Logger](
  topic: String,
  producer: KafkaProducer[F, String, String]
) extends QuestEstimationEventProducerAlgebra[F] {

  override def estimationFinalized(event: QuestEstimationFinalized): F[KafkaProducerResult] = {
    val key = event.questId
    val value = event.asJson.noSpaces // Use Circe to convert to JSON string

    val record = ProducerRecord(topic, key, value)
    val message = ProducerRecords.one(record)

    for {
      _ <- Logger[F].info(s"[Kafka] Producing estimationFinalized event for quest ${event.questId}")
      result <- producer.produce(message).flatten.as(SuccessfulWrite)
    } yield result
  }
}
