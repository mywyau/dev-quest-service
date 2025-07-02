package models.rewards

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.RewardStatus

case class UpdateRewardData(
  timeRewardValue: BigDecimal,
  completionRewardValue: BigDecimal
)

object UpdateRewardData {
  implicit val encoder: Encoder[UpdateRewardData] = deriveEncoder[UpdateRewardData]
  implicit val decoder: Decoder[UpdateRewardData] = deriveDecoder[UpdateRewardData]
}
