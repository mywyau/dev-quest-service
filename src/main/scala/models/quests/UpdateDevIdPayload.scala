package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.QuestStatus

case class UpdateDevIdPayload(
  questId: String,
  devId: String
)

object UpdateDevIdPayload {
  implicit val encoder: Encoder[UpdateDevIdPayload] = deriveEncoder[UpdateDevIdPayload]
  implicit val decoder: Decoder[UpdateDevIdPayload] = deriveDecoder[UpdateDevIdPayload]
}
