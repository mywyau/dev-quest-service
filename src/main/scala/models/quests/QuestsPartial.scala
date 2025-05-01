package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.QuestStatus

case class QuestsPartial(
  userId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object QuestsPartial {
  implicit val encoder: Encoder[QuestsPartial] = deriveEncoder[QuestsPartial]
  implicit val decoder: Decoder[QuestsPartial] = deriveDecoder[QuestsPartial]
}
