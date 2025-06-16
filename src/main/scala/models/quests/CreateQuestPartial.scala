package models.quests

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.Rank

import java.time.LocalDateTime

case class CreateQuestPartial(
  rank: Rank,
  title: String,
  description: Option[String],
  acceptanceCriteria: String
)

object CreateQuestPartial {
  implicit val encoder: Encoder[CreateQuestPartial] = deriveEncoder[CreateQuestPartial]
  implicit val decoder: Decoder[CreateQuestPartial] = deriveDecoder[CreateQuestPartial]
}
