package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.languages.Language
import models.rewards.RewardData
import models.QuestStatus
import models.Rank

case class QuestWithReward(
  quest: QuestPartial,
  reward: Option[RewardData]
)

object QuestWithReward {
  implicit val encoder: Encoder[QuestWithReward] = deriveEncoder[QuestWithReward]
  implicit val decoder: Decoder[QuestWithReward] = deriveDecoder[QuestWithReward]
}
