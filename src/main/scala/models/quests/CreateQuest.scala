package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.QuestStatus

case class CreateQuest(
  userId: String,
  questId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object CreateQuest {
  implicit val encoder: Encoder[CreateQuest] = deriveEncoder[CreateQuest]
  implicit val decoder: Decoder[CreateQuest] = deriveDecoder[CreateQuest]
}
