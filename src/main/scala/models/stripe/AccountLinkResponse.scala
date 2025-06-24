package models.stripe

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class AccountLinkResponse(
  url: String
)

object AccountLinkResponse {
  implicit val encoder: Encoder[AccountLinkResponse] = deriveEncoder[AccountLinkResponse]
  implicit val decoder: Decoder[AccountLinkResponse] = deriveDecoder[AccountLinkResponse]
}
