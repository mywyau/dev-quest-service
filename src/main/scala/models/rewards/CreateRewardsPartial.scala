package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.RewardStatus

case class CreateRewardPartial(
  baseReward: BigDecimal,
  timeReward: BigDecimal,
  completionReward: BigDecimal
)

object CreateRewardPartial {
  implicit val encoder: Encoder[CreateRewardPartial] = deriveEncoder[CreateRewardPartial]
  implicit val decoder: Decoder[CreateRewardPartial] = deriveDecoder[CreateRewardPartial]
}
