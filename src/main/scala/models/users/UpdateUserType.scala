package models.users

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.UserType

case class UpdateUserType(
  username: String,
  userType: UserType
)

object UpdateUserType {
  implicit val encoder: Encoder[UpdateUserType] = deriveEncoder[UpdateUserType]
  implicit val decoder: Decoder[UpdateUserType] = deriveDecoder[UpdateUserType]
}
