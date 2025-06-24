// ./models/payment/

package models.payment

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class CheckoutSessionUrl(
  url: String
)

object CheckoutSessionUrl {
  implicit val encoder: Encoder[CheckoutSessionUrl] = deriveEncoder[CheckoutSessionUrl]
  implicit val decoder: Decoder[CheckoutSessionUrl] = deriveDecoder[CheckoutSessionUrl]
}
