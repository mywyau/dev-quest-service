package models.quests

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.Rank

case class UpdateQuestPartial(
  rank: Rank,
  title: String,
  description: Option[String],
  acceptanceCriteria: Option[String]
)

object UpdateQuestPartial {
  implicit val encoder: Encoder[UpdateQuestPartial] = deriveEncoder[UpdateQuestPartial]
  implicit val decoder: Decoder[UpdateQuestPartial] = deriveDecoder[UpdateQuestPartial]
}
