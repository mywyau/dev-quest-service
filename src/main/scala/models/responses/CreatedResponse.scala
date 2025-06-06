package models.responses

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class CreatedResponse(code:String, message: String)

object CreatedResponse {
  implicit val encoder: Encoder[CreatedResponse] = deriveEncoder[CreatedResponse]
  implicit val decoder: Decoder[CreatedResponse] = deriveDecoder[CreatedResponse]
}
