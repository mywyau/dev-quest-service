package models.kafka

import io.circe.generic.semiauto.*
import io.circe.Decoder
import io.circe.Encoder
import java.time.Instant
import models.Rank

case class QuestEstimationFinalized(
  questId: String,
  finalRank: Rank,
  finalizedAt: Instant
)

object QuestEstimationFinalized {

  given Encoder[QuestEstimationFinalized] = deriveEncoder
  given Decoder[QuestEstimationFinalized] = deriveDecoder
}
