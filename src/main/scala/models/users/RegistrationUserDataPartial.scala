package models.users

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.UserType

case class RegistrationUserDataPartial(
  userId: String,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  userType: Option[UserType]
)

object RegistrationUserDataPartial {
  implicit val encoder: Encoder[RegistrationUserDataPartial] = deriveEncoder[RegistrationUserDataPartial]
  implicit val decoder: Decoder[RegistrationUserDataPartial] = deriveDecoder[RegistrationUserDataPartial]
}
