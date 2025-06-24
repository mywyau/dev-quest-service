package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.RewardStatus

case class RewardPartial(
  questId: String,
  clientId: String,
  devId: String,
  dollarValue: BigDecimal,
  paid: RewardStatus
)

object RewardPartial {
  implicit val encoder: Encoder[RewardPartial] = deriveEncoder[RewardPartial]
  implicit val decoder: Decoder[RewardPartial] = deriveDecoder[RewardPartial]
}
