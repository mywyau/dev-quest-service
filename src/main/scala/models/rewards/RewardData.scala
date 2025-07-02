package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.RewardStatus

case class RewardData(
  questId: String,
  clientId: String,
  devId: Option[String],
  timeRewardValue: BigDecimal,
  completionRewardValue: BigDecimal,
  paid: RewardStatus
)

object RewardData {
  implicit val encoder: Encoder[RewardData] = deriveEncoder[RewardData]
  implicit val decoder: Decoder[RewardData] = deriveDecoder[RewardData]
}
