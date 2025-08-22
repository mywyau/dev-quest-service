package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait PricingPlans

case object ClientFree extends PricingPlans
case object ClientStarter extends PricingPlans
case object ClientGrowth extends PricingPlans
case object ClientScale extends PricingPlans
case object DevFree extends PricingPlans
case object DevFreelancer extends PricingPlans
case object DevPro extends PricingPlans

object PricingPlans {

  def fromString(str: String): PricingPlans =
    str match {
      case "ClientFree" => ClientFree
      case "ClientStarter" => ClientStarter
      case "ClientGrowth" => ClientGrowth
      case "ClientScale" => ClientScale
      case "DevFree" => DevFree
      case "DevFreelancer" => DevFreelancer
      case "DevPro" => DevPro
      case _ => throw new Exception(s"Unknown PricingPlans type: $str")
    }

  implicit val pricingPlansEncoder: Encoder[PricingPlans] =
    Encoder.encodeString.contramap {
      case ClientFree => "ClientFree"
      case ClientStarter => "ClientStarter"
      case ClientGrowth => "ClientGrowth"
      case ClientScale => "ClientScale"
      case DevFree => "DevFree"
      case DevFreelancer => "DevFreelancer"
      case DevPro => "DevPro"
    }

  implicit val pricingPlansDecoder: Decoder[PricingPlans] =
    Decoder.decodeString.emap {
      case "ClientFree" => Right(ClientFree)
      case "ClientStarter" => Right(ClientStarter)
      case "ClientGrowth" => Right(ClientGrowth)
      case "ClientScale" => Right(ClientScale)
      case "DevFree" => Right(DevFree)
      case "DevFreelancer" => Right(DevFreelancer)
      case "DevPro" => Right(DevPro)
      case other => Left(s"Invalid PricingPlans: $other")
    }
}
