package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.QuestStatus
import models.Rank

case class CompleteQuestPayload(
  rank: Rank,
  questStatus: QuestStatus
)

object CompleteQuestPayload {
  implicit val encoder: Encoder[CompleteQuestPayload] = deriveEncoder[CompleteQuestPayload]
  implicit val decoder: Decoder[CompleteQuestPayload] = deriveDecoder[CompleteQuestPayload]
}
