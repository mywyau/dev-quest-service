package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.QuestStatus

case class UpdateQuestPartial(
  userId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object UpdateQuestPartial {
  implicit val encoder: Encoder[UpdateQuestPartial] = deriveEncoder[UpdateQuestPartial]
  implicit val decoder: Decoder[UpdateQuestPartial] = deriveDecoder[UpdateQuestPartial]
}
