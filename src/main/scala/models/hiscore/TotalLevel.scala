package models.hiscore

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class TotalLevel(
  devId: String,
  username: String,
  totalLevel: Int,
  totalXP: BigDecimal
)

object TotalLevel {
  implicit val encoder: Encoder[TotalLevel] = deriveEncoder[TotalLevel]
  implicit val decoder: Decoder[TotalLevel] = deriveDecoder[TotalLevel]
}
