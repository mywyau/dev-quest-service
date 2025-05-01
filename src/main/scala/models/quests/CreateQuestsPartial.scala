package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.QuestStatus

case class CreateQuestsPartial(
  userId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object CreateQuestsPartial {
  implicit val createQuestsPartialEncoder: Encoder[CreateQuestsPartial] = deriveEncoder[CreateQuestsPartial]
  implicit val createQuestsPartialDecoder: Decoder[CreateQuestsPartial] = deriveDecoder[CreateQuestsPartial]
}
