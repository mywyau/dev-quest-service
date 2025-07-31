package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime

case class CreateCompletionReward(
  questId: String,
  completionRewardValue: Int // cents
)

object CreateCompletionReward {
  implicit val encoder: Encoder[CreateCompletionReward] = deriveEncoder[CreateCompletionReward]
  implicit val decoder: Decoder[CreateCompletionReward] = deriveDecoder[CreateCompletionReward]
}
