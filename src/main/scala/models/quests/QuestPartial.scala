package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.QuestStatus

case class QuestPartial(
  userId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object QuestPartial {
  implicit val encoder: Encoder[QuestPartial] = deriveEncoder[QuestPartial]
  implicit val decoder: Decoder[QuestPartial] = deriveDecoder[QuestPartial]
}
