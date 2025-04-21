package models.responses

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class GetResponse(code:String, message: String)

object GetResponse {
  implicit val encoder: Encoder[GetResponse] = deriveEncoder[GetResponse]
  implicit val decoder: Decoder[GetResponse] = deriveDecoder[GetResponse]
}
