package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.QuestStatus

case class CreateQuestPartial(
  title: String,
  description: Option[String]
)

object CreateQuestPartial {
  implicit val encoder: Encoder[CreateQuestPartial] = deriveEncoder[CreateQuestPartial]
  implicit val decoder: Decoder[CreateQuestPartial] = deriveDecoder[CreateQuestPartial]
}
