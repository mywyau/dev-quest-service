package models.auth

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class UserSession(
  userId: String,
  cookieValue: String,
  email: String,
  userType: String
)

object UserSession {
  implicit val encoder: Encoder[UserSession] = deriveEncoder[UserSession]
  implicit val decoder: Decoder[UserSession] = deriveDecoder[UserSession]
}
