package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.RewardStatus

case class CreateReward(
  baseReward: BigDecimal,
  timeReward: BigDecimal,
  completionReward: BigDecimal
)

object CreateReward {
  implicit val encoder: Encoder[CreateReward] = deriveEncoder[CreateReward]
  implicit val decoder: Decoder[CreateReward] = deriveDecoder[CreateReward]
}
