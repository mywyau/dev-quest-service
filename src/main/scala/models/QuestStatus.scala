package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait QuestStatus

case object InReview extends QuestStatus
case object NotStarted extends QuestStatus
case object InProgress extends QuestStatus
case object Completed extends QuestStatus


object QuestStatus {

  def fromString(str: String): QuestStatus =
    str match {
      case "InReview" => InReview
      case "NotStarted" => NotStarted
      case "InProgress" => InProgress
      case "Completed" => Completed
      case _ => throw new Exception(s"Unknown QuestStatus type: $str")
    }

  implicit val questStatusEncoder: Encoder[QuestStatus] =
    Encoder.encodeString.contramap {
      case InReview => "InReview"
      case NotStarted => "NotStarted"
      case InProgress => "InProgress"
      case Completed => "Completed"
    }

  implicit val QuestStatusDecoder: Decoder[QuestStatus] =
    Decoder.decodeString.emap {
      case "InReview" => Right(InReview)
      case "NotStarted" => Right(NotStarted)
      case "InProgress" => Right(InProgress)
      case "Completed" => Right(Completed)
      case other => Left(s"Invalid QuestStatus: $other")
    }
}
