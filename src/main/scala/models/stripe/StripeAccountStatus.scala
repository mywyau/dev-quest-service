package models.stripe

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class StripeAccountStatus(
  stripeAccountId: String,
  chargesEnabled: Boolean,
  payoutsEnabled: Boolean,
  detailsSubmitted: Boolean
)

object StripeAccountStatus {
  implicit val encoder: Encoder[StripeAccountStatus] = deriveEncoder[StripeAccountStatus]
  implicit val decoder: Decoder[StripeAccountStatus] = deriveDecoder[StripeAccountStatus]
}
