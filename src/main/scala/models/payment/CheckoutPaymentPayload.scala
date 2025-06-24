// ./models/payment/

package models.payment

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class CheckoutPaymentPayload(
  developerStripeId: String,
  amountCents: Long
)

object CheckoutPaymentPayload {
  implicit val encoder: Encoder[CheckoutPaymentPayload] = deriveEncoder[CheckoutPaymentPayload]
  implicit val decoder: Decoder[CheckoutPaymentPayload] = deriveDecoder[CheckoutPaymentPayload]
}
