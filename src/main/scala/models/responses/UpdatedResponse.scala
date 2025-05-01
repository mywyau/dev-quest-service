package models.responses

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class UpdatedResponse(code: String, message: String)

object UpdatedResponse {
  implicit val encoder: Encoder[UpdatedResponse] = deriveEncoder[UpdatedResponse]
  implicit val decoder: Decoder[UpdatedResponse] = deriveDecoder[UpdatedResponse]
}
