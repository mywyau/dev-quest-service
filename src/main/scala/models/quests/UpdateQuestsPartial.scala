package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.QuestStatus

case class UpdateQuestsPartial(
  userId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object UpdateQuestsPartial {
  implicit val encoder: Encoder[UpdateQuestsPartial] = deriveEncoder[UpdateQuestsPartial]
  implicit val decoder: Decoder[UpdateQuestsPartial] = deriveDecoder[UpdateQuestsPartial]
}
