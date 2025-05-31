package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.QuestStatus

case class AcceptQuestPayload(
  devId: String,
  questId: String
)

object AcceptQuestPayload {
  implicit val encoder: Encoder[AcceptQuestPayload] = deriveEncoder[AcceptQuestPayload]
  implicit val decoder: Decoder[AcceptQuestPayload] = deriveDecoder[AcceptQuestPayload]
}
