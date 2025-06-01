package models.quests

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.QuestStatus

import java.time.LocalDateTime

case class CreateQuest(
  questId: String,
  clientId: String,
  title: String,
  description: Option[String],
  status: Option[QuestStatus]
)

object CreateQuest {
  implicit val encoder: Encoder[CreateQuest] = deriveEncoder[CreateQuest]
  implicit val decoder: Decoder[CreateQuest] = deriveDecoder[CreateQuest]
}
