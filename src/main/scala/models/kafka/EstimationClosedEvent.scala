package models.kafka

// models/kafka/EstimationClosedEvent.scala
package models.kafka

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import java.time.Instant

final case class EstimationClosedEvent(
  questId: String,
  closedAt: Instant
)

object EstimationClosedEvent {
  given Encoder[EstimationClosedEvent] = deriveEncoder
  given Decoder[EstimationClosedEvent] = deriveDecoder
}
