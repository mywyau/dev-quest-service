package models.skills

import io.circe.Decoder
import io.circe.Encoder

sealed trait Skill

case object Estimating extends Skill
case object Questing extends Skill
case object Testing extends Skill

object Skill {

  def fromString(str: String): Skill =
    str match {
      case "Estimating" => Estimating
      case "Questing" => Questing
      case "Testing" => Testing
      case _ => throw new Exception(s"Unknown Skill type: $str")
    }

  implicit val questStatusEncoder: Encoder[Skill] =
    Encoder.encodeString.contramap {
      case Estimating => "Estimating"
      case Questing => "Questing"
      case Testing => "Testing"
    }

  implicit val SkillDecoder: Decoder[Skill] =
    Decoder.decodeString.emap {
      case "Estimating" => Right(Estimating)
      case "Questing" => Right(Questing)
      case "Testing" => Right(Testing)
      case other => Left(s"Invalid Skill: $other")
    }
}
