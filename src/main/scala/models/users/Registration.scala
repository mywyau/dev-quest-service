package models.users

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.UserType

case class Registration(
  username: String,
  firstName: String,
  lastName: String,
  userType: UserType
)

object Registration {
  implicit val encoder: Encoder[Registration] = deriveEncoder[Registration]
  implicit val decoder: Decoder[Registration] = deriveDecoder[Registration]
}
