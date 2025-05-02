package models.responses

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class DeletedResponse(code: String, message: String)

object DeletedResponse {
  implicit val encoder: Encoder[DeletedResponse] = deriveEncoder[DeletedResponse]
  implicit val decoder: Decoder[DeletedResponse] = deriveDecoder[DeletedResponse]
}
