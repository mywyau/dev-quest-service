package models.kafka

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*
import models.Rank
import models.estimate.Estimate

import java.time.Instant

case class QuestEstimationFinalized(
  questId: String,
  finalRank: Rank,
  baseXp: Double,
  finalizedAt: Instant,
  userEstimates: List[UserEstimate] // we send a list of user estimates to know who to award xp to.
)

object QuestEstimationFinalized {

  given Encoder[QuestEstimationFinalized] = deriveEncoder
  given Decoder[QuestEstimationFinalized] = deriveDecoder
}
