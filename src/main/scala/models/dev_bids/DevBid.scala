package models.dev_bids

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.LocalDateTime

case class DevBid(
  bid: BigDecimal,
)

object DevBid {
  implicit val encoder: Encoder[DevBid] = deriveEncoder[DevBid]
  implicit val decoder: Decoder[DevBid] = deriveDecoder[DevBid]
}
