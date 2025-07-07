package models.users

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.UserType

import java.time.LocalDateTime

case class UserData(
  userId: String,
  username: String,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  userType: Option[UserType]
)

object UserData {
  implicit val encoder: Encoder[UserData] = deriveEncoder[UserData]
  implicit val decoder: Decoder[UserData] = deriveDecoder[UserData]
}
