package models.users

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.UserType

case class UpdateUserData(
  userId: String,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  userType: Option[UserType]
)

object UpdateUserData {
  implicit val encoder: Encoder[UpdateUserData] = deriveEncoder[UpdateUserData]
  implicit val decoder: Decoder[UpdateUserData] = deriveDecoder[UpdateUserData]
}
