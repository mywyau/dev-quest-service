// ./models/payment/

package models.payment

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class StripePaymentIntent(
  clientSecret: String
)

object StripePaymentIntent {
  implicit val encoder: Encoder[StripePaymentIntent] = deriveEncoder[StripePaymentIntent]
  implicit val decoder: Decoder[StripePaymentIntent] = deriveDecoder[StripePaymentIntent]
}