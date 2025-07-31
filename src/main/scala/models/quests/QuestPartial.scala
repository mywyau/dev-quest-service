package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.Instant
import java.time.LocalDateTime
import models.languages.Language
import models.QuestStatus
import models.Rank

case class QuestPartial(
  questId: String,
  clientId: String,
  devId: Option[String],
  rank: Rank,
  title: String,
  hoursOfWork: Option[Double],
  description: Option[String],
  acceptanceCriteria: Option[String],
  status: Option[QuestStatus],
  tags: Seq[String],
  estimationCloseAt: Option[Instant],
  estimated: Boolean
)

object QuestPartial {
  implicit val encoder: Encoder[QuestPartial] = deriveEncoder[QuestPartial]
  implicit val decoder: Decoder[QuestPartial] = deriveDecoder[QuestPartial]
}
