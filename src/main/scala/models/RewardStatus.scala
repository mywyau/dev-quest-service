package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait RewardStatus

case object Paid extends RewardStatus
case object NotPaid extends RewardStatus
case object Pending extends RewardStatus

object RewardStatus {

  def fromString(str: String): RewardStatus =
    str match {
      case "Paid" => Paid
      case "Pending" => Pending
      case "NotPaid" => NotPaid
      case _ => throw new Exception(s"Unknown RewardStatus type: $str")
    }

  implicit val rewardStatusEncoder: Encoder[RewardStatus] =
    Encoder.encodeString.contramap {
      case Paid => "Paid"
      case Pending => "Pending"
      case NotPaid => "NotPaid"
    }

  implicit val rewardStatusDecoder: Decoder[RewardStatus] =
    Decoder.decodeString.emap {
      case "Paid" => Right(Paid)
      case "Pending" => Right(Pending)
      case "NotPaid" => Right(NotPaid)
      case other => Left(s"Invalid RewardStatus: $other")
    }
}
