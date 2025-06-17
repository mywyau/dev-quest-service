package models.skills

import io.circe.Decoder
import io.circe.Encoder

sealed trait Skill

case object Questing extends Skill
case object Reviewing extends Skill
case object Testing extends Skill

object Skill {

  def fromString(str: String): Skill =
    str match {
      case "Questing" => Questing
      case "Reviewing" => Reviewing
      case "Testing" => Testing
      case _ => throw new Exception(s"Unknown Skill type: $str")
    }

  implicit val skillEncoder: Encoder[Skill] =
    Encoder.encodeString.contramap {
      case Questing => "Questing"
      case Reviewing => "Reviewing"
      case Testing => "Testing"
    }

  implicit val skillDecoder: Decoder[Skill] =
    Decoder.decodeString.emap {
      case "Questing" => Right(Questing)
      case "Reviewing" => Right(Reviewing)
      case "Testing" => Right(Testing)
      case other => Left(s"Invalid Skill: $other")
    }
}
