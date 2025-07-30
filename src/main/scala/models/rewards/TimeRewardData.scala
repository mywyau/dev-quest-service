package models.rewards

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.RewardStatus

case class TimeRewardData(
  questId: String,
  clientId: String,
  devId: Option[String],
  timeRewardValue: BigDecimal
)

object TimeRewardData {
  implicit val encoder: Encoder[TimeRewardData] = deriveEncoder[TimeRewardData]
  implicit val decoder: Decoder[TimeRewardData] = deriveDecoder[TimeRewardData]
}
