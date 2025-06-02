package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.QuestStatus

case class UpdateQuestStatusPayload(
  questStatus: QuestStatus
)

object UpdateQuestStatusPayload {
  implicit val encoder: Encoder[UpdateQuestStatusPayload] = deriveEncoder[UpdateQuestStatusPayload]
  implicit val decoder: Decoder[UpdateQuestStatusPayload] = deriveDecoder[UpdateQuestStatusPayload]
}
