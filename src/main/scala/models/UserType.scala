package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait UserType

case object Client extends UserType
case object Dev extends UserType

object UserType {

  def fromString(str: String): UserType =
    str match {
      case "Client" => Client
      case "Dev" => Dev
      case _ => throw new Exception(s"Unknown UserType type: $str")
    }

  implicit val UserTypeEncoder: Encoder[UserType] =
    Encoder.encodeString.contramap {
      case Client => "Client"
      case Dev => "Dev"
    }

  implicit val userTypeDecoder: Decoder[UserType] =
    Decoder.decodeString.emap {
      case "Client" => Right(Client)
      case "Dev" => Right(Dev)
      case other => Left(s"[UserType] Invalid UserType: $other")
    }
}
