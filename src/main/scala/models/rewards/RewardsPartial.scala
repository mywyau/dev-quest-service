package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.RewardStatus

case class RewardPartial(
  baseReward: BigDecimal,
  timeReward: BigDecimal,
  completionReward: BigDecimal
)

object RewardPartial {
  implicit val encoder: Encoder[RewardPartial] = deriveEncoder[RewardPartial]
  implicit val decoder: Decoder[RewardPartial] = deriveDecoder[RewardPartial]
}
