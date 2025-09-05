package controllers.mocks

import cats.Applicative
import cats.implicits.*
import models.events.QuestCreatedEvent
import models.kafka.KafkaProducerResult
import models.kafka.SuccessfulWrite
import services.kafka.producers.QuestEventProducerAlgebra

class MockQuestEventProducer[F[_] : Applicative] extends QuestEventProducerAlgebra[F] {
  def publishQuestCreated(event: QuestCreatedEvent): F[KafkaProducerResult] = SuccessfulWrite.pure[F]
}
