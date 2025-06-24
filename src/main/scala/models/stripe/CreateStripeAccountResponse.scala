package models.stripe

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class CreateStripeAccountResponse(
  accountId: String
)

object CreateStripeAccountResponse {
  implicit val encoder: Encoder[CreateStripeAccountResponse] = deriveEncoder[CreateStripeAccountResponse]
  implicit val decoder: Decoder[CreateStripeAccountResponse] = deriveDecoder[CreateStripeAccountResponse]
}
