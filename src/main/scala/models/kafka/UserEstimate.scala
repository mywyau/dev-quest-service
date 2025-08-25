package models.kafka

import io.circe.generic.semiauto.*
import io.circe.Decoder
import io.circe.Encoder

case class UserEstimate(
  devId: String,
  username: String,
  score: Int,
  hours: BigDecimal,
  modifier: BigDecimal
)

object UserEstimate {

  given Encoder[UserEstimate] = deriveEncoder
  given Decoder[UserEstimate] = deriveDecoder

}
