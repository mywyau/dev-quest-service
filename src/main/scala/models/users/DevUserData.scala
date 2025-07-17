package models.users

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.UserType

import java.time.LocalDateTime

case class DevUserData(
  userId: String,
  username: String,
  email: String,
  mobile: String,
  firstName: Option[String],
  lastName: Option[String]
)

object DevUserData {
  implicit val encoder: Encoder[DevUserData] = deriveEncoder[DevUserData]
  implicit val decoder: Decoder[DevUserData] = deriveDecoder[DevUserData]
}
