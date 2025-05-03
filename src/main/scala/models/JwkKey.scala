package models

import io.circe.Decoder
import io.circe.generic.semiauto._

case class JwkKey(
  kty: String,
  use: String,
  n: String,
  e: String,
  kid: String,
  x5t: Option[String],
  x5c: Option[List[String]],
  alg: Option[String]
)

case class JwksResponse(keys: List[JwkKey])

object JwksResponse {
  implicit val jwkKeyDecoder: Decoder[JwkKey] = deriveDecoder
  implicit val jwksResponseDecoder: Decoder[JwksResponse] = deriveDecoder
}
