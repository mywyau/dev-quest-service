package models.stripe

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class StripeDevUserData(
  userId: String
)

object StripeDevUserData {
  implicit val encoder: Encoder[StripeDevUserData] = deriveEncoder[StripeDevUserData]
  implicit val decoder: Decoder[StripeDevUserData] = deriveDecoder[StripeDevUserData]
}
