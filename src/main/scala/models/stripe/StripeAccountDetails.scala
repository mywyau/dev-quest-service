package models.stripe

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class StripeAccountDetails(
  userId: String,
  stripeAccountId: String,
  onboarded: String,
  chargesEnabled: Option[String],
  payoutsEnabled: Option[String]
)

object StripeAccountDetails {
  implicit val encoder: Encoder[StripeAccountDetails] = deriveEncoder[StripeAccountDetails]
  implicit val decoder: Decoder[StripeAccountDetails] = deriveDecoder[StripeAccountDetails]
}
