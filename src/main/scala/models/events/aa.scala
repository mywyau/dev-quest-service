// models/events/QuestCreatedEvent.scala
package models.events

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import java.time.Instant

case class QuestCreatedEvent(
  questId: String,
  title: String,
  clientId: String,
  createdAt: Instant
)

object QuestCreatedEvent {
  given Encoder[QuestCreatedEvent] = deriveEncoder
  given Decoder[QuestCreatedEvent] = deriveDecoder
}
