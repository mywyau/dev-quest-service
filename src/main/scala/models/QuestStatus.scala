package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait QuestStatus

case object Review extends QuestStatus
case object NotStarted extends QuestStatus
case object InProgress extends QuestStatus
case object Completed extends QuestStatus
case object Failed extends QuestStatus
case object Submitted extends QuestStatus
case object Assigned extends QuestStatus
case object PaidOut extends QuestStatus
case object Open extends QuestStatus

object QuestStatus {

  def fromString(str: String): QuestStatus =
    str match {
      case "Review" => Review
      case "NotStarted" => NotStarted
      case "InProgress" => InProgress
      case "Completed" => Completed
      case "Failed" => Failed
      case "Submitted" => Submitted
      case "Assigned" => Assigned
      case "PaidOut" => PaidOut
      case "Open" => Open
      case _ => throw new Exception(s"Unknown QuestStatus type: $str")
    }

  implicit val questStatusEncoder: Encoder[QuestStatus] =
    Encoder.encodeString.contramap {
      case Review => "Review"
      case NotStarted => "NotStarted"
      case InProgress => "InProgress"
      case Completed => "Completed"
      case Failed => "Failed"
      case Submitted => "Submitted"
      case Assigned => "Assigned"
      case PaidOut => "PaidOut"
      case Open => "Open"
    }

  implicit val QuestStatusDecoder: Decoder[QuestStatus] =
    Decoder.decodeString.emap {
      case "Review" => Right(Review)
      case "NotStarted" => Right(NotStarted)
      case "InProgress" => Right(InProgress)
      case "Completed" => Right(Completed)
      case "Failed" => Right(Failed)
      case "Submitted" => Right(Submitted)
      case "Assigned" => Right(Assigned)
      case "PaidOut" => Right(PaidOut)
      case "Open" => Right(Open)
      case other => Left(s"Invalid QuestStatus: $other")
    }
}
