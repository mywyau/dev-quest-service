package models.rewards

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.RewardStatus

case class UpdateRewardsPartial(
  baseReward: BigDecimal,
  timeReward: BigDecimal,
  completionReward: BigDecimal
)

object UpdateRewardsPartial {
  implicit val encoder: Encoder[UpdateRewardsPartial] = deriveEncoder[UpdateRewardsPartial]
  implicit val decoder: Decoder[UpdateRewardsPartial] = deriveDecoder[UpdateRewardsPartial]
}
