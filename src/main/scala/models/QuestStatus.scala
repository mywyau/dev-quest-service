package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait QuestStatus

case object Draft extends QuestStatus
case object Cancelled extends QuestStatus
case object Expired extends QuestStatus
case object NotEstimated extends QuestStatus
case object Estimated extends QuestStatus
case object Open extends QuestStatus
case object NotStarted extends QuestStatus
case object InProgress extends QuestStatus
case object Rejected extends QuestStatus
case object Review extends QuestStatus
case object Completed extends QuestStatus
case object Failed extends QuestStatus
case object Submitted extends QuestStatus
case object PaidOut extends QuestStatus


object QuestStatus {


  def fromString(str: String): QuestStatus =
    str match {
      case "Draft" => Draft
      case "Cancelled" => Cancelled
      case "Expired" => Expired
      case "NotEstimated" => NotEstimated
      case "Estimated" => Estimated
      case "Review" => Review
      case "Rejected" => Rejected
      case "NotStarted" => NotStarted
      case "InProgress" => InProgress
      case "Completed" => Completed
      case "Failed" => Failed
      case "Submitted" => Submitted
      case "PaidOut" => PaidOut
      case "Open" => Open               // this is a set to public status move from estimated into publicly viewable  
      case _ => throw new Exception(s"Unknown QuestStatus type: $str")
    }

  implicit val questStatusEncoder: Encoder[QuestStatus] =
    Encoder.encodeString.contramap {
      case Draft => "Draft"
      case Cancelled => "Cancelled"
      case Expired => "Expired"
      case NotEstimated => "NotEstimated"
      case Estimated => "Estimated"
      case Review => "Review"
      case Rejected => "Rejected"
      case NotStarted => "NotStarted"
      case InProgress => "InProgress"
      case Completed => "Completed"
      case Failed => "Failed"
      case Submitted => "Submitted"
      case PaidOut => "PaidOut"
      case Open => "Open"
    }

  implicit val QuestStatusDecoder: Decoder[QuestStatus] =
    Decoder.decodeString.emap {
      case "Draft" => Right(Draft)
      case "Cancelled" => Right(Cancelled)
      case "Expired" => Right(Expired)
      case "NotEstimated" => Right(NotEstimated)
      case "Estimated" => Right(Estimated)
      case "Review" => Right(Review)
      case "Rejected" => Right(Rejected)
      case "NotStarted" => Right(NotStarted)
      case "InProgress" => Right(InProgress)
      case "Completed" => Right(Completed)
      case "Failed" => Right(Failed)
      case "Submitted" => Right(Submitted)
      case "PaidOut" => Right(PaidOut)
      case "Open" => Right(Open)
      case other => Left(s"Invalid QuestStatus: $other")
    }
}
