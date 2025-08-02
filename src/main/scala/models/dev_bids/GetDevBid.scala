package models.dev_bids

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.LocalDateTime

case class GetDevBid(
  devUsername: String,
  bid: BigDecimal,
)

object GetDevBid {
  implicit val encoder: Encoder[GetDevBid] = deriveEncoder[GetDevBid]
  implicit val decoder: Decoder[GetDevBid] = deriveDecoder[GetDevBid]
}
