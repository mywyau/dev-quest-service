package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime

case class CreateTimeReward(
  questId: String,
  timeRewardValue: Int
)

object CreateTimeReward {
  implicit val encoder: Encoder[CreateTimeReward] = deriveEncoder[CreateTimeReward]
  implicit val decoder: Decoder[CreateTimeReward] = deriveDecoder[CreateTimeReward]
}
