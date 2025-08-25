// models/events/EstimationEvents.scala
package models.events

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.time.Instant

final case class EstimationSubmittedEvent(
  questId: String,
  devId: String,
  clientId: String,
  score: Int,          // 1..100
  days: Int,           // integer days
  submittedAt: Instant
)
object EstimationSubmittedEvent {
  given Encoder[EstimationSubmittedEvent] = deriveEncoder
  given Decoder[EstimationSubmittedEvent] = deriveDecoder
}

final case class EstimationFinalizedEvent(
  questId: String,
  finalizedAt: Instant
)
object EstimationFinalizedEvent {
  given Encoder[EstimationFinalizedEvent] = deriveEncoder
  given Decoder[EstimationFinalizedEvent] = deriveDecoder
}
