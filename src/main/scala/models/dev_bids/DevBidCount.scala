package models.dev_bids

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class DevBidCount(numberOfDevBids:Int)

object DevBidCount {
  implicit val encoder: Encoder[DevBidCount] = deriveEncoder[DevBidCount]
  implicit val decoder: Decoder[DevBidCount] = deriveDecoder[DevBidCount]
}
