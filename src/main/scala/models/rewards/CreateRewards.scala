package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime

case class CreateReward(
  questId: String,
  rewardValue: Int
)

object CreateReward {
  implicit val encoder: Encoder[CreateReward] = deriveEncoder[CreateReward]
  implicit val decoder: Decoder[CreateReward] = deriveDecoder[CreateReward]
}
