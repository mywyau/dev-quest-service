package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait EstimationStatus

case object EstimateOpen extends EstimationStatus
case object EstimateClosed extends EstimationStatus

object EstimationStatus {

  def fromString(str: String): EstimationStatus =
    str match {
      case "EstimateOpen" => EstimateOpen
      case "EstimateClosed" => EstimateClosed
      case _ => throw new Exception(s"Unknown EstimationStatus type: $str")
    }

  implicit val estimationStatusEncoder: Encoder[EstimationStatus] =
    Encoder.encodeString.contramap {
      case EstimateOpen => "EstimateOpen"
      case EstimateClosed => "EstimateClosed"
    }

  implicit val estimationStatusDecoder: Decoder[EstimationStatus] =
    Decoder.decodeString.emap {
      case "EstimateOpen" => Right(EstimateOpen)
      case "EstimateClosed" => Right(EstimateClosed)
      case other => Left(s"Invalid EstimationStatus: $other")
    }
}
