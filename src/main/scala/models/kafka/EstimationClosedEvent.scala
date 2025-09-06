package models.kafka

// models/kafka/EstimationClosedEvent.scala

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*

import java.time.Instant

final case class EstimationClosedEvent(
  questId: String,
  closedAt: Instant
)

object EstimationClosedEvent {
  given Encoder[EstimationClosedEvent] = deriveEncoder
  given Decoder[EstimationClosedEvent] = deriveDecoder
}
