package models.quests

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.QuestStatus

import java.time.LocalDateTime

case class QuestPartial(
  questId: String,
  clientId: String,
  devId: Option[String],
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object QuestPartial {
  implicit val encoder: Encoder[QuestPartial] = deriveEncoder[QuestPartial]
  implicit val decoder: Decoder[QuestPartial] = deriveDecoder[QuestPartial]
}
