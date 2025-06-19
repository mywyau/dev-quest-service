package models.skills

import io.circe.Decoder
import io.circe.Encoder

sealed trait Skill

case object Reviewing extends Skill
case object Questing extends Skill
case object Testing extends Skill

object Skill {

  def fromString(str: String): Skill =
    str match {
      case "Reviewing" => Reviewing
      case "Questing" => Questing
      case "Testing" => Testing
      case _ => throw new Exception(s"Unknown Skill type: $str")
    }

  implicit val questStatusEncoder: Encoder[Skill] =
    Encoder.encodeString.contramap {
      case Reviewing => "Reviewing"
      case Questing => "Questing"
      case Testing => "Testing"
    }

  implicit val SkillDecoder: Decoder[Skill] =
    Decoder.decodeString.emap {
      case "Reviewing" => Right(Reviewing)
      case "Questing" => Right(Questing)
      case "Testing" => Right(Testing)
      case other => Left(s"Invalid Skill: $other")
    }
}
