package models.users

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.UserType

case class UserData(
  userId: String,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  userType: Option[UserType]
)

object UserData {
  implicit val encoder: Encoder[UserData] = deriveEncoder[UserData]
  implicit val decoder: Decoder[UserData] = deriveDecoder[UserData]
}
