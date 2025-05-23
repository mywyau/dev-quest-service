package models.users

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.UserType

case class CreateUserData(
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  userType: Option[UserType]
)

object CreateUserData {
  implicit val encoder: Encoder[CreateUserData] = deriveEncoder[CreateUserData]
  implicit val decoder: Decoder[CreateUserData] = deriveDecoder[CreateUserData]
}
