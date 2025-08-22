package models.pricing

import io.circe.Decoder
import io.circe.Encoder

sealed trait UserPricingPlanStatus

case object Active extends UserPricingPlanStatus // Paying customer, plan is active
case object Trialing extends UserPricingPlanStatus // On a trial period
case object PastDue extends UserPricingPlanStatus // Payment failed, grace period
case object Canceled extends UserPricingPlanStatus // Subscription ended
case object Incomplete extends UserPricingPlanStatus // Checkout started but not completed
case object IncompleteExpired extends UserPricingPlanStatus // Incomplete and expired
case object Paused extends UserPricingPlanStatus // Temporarily paused (optional)
case object Unpaid extends UserPricingPlanStatus // Payment failed permanently

object UserPricingPlanStatus {

  def fromString(str: String): UserPricingPlanStatus =
    str match {
      case "Active" => Active
      case "Trialing" => Trialing
      case "PastDue" => PastDue
      case "Canceled" => Canceled
      case "Incomplete" => Incomplete
      case "IncompleteExpired" => IncompleteExpired
      case "Paused" => Paused
      case "Unpaid" => Unpaid
      case other => 
        throw new Exception(s"Unknown UserPricingPlanStatus: $other")
    }

    // new: Option-returning variant
  def fromStringOpt(str: String): Option[UserPricingPlanStatus] =
    scala.util.Try(fromString(str)).toOption

  // new: tolerant Stripe mapping (lowercase + underscores)
  def fromStripeStatus(str: String): UserPricingPlanStatus =
    str.toLowerCase match {
      case "active" => Active
      case "trialing" => Trialing
      case "past_due" => PastDue
      case "canceled" => Canceled
      case "incomplete" => Incomplete
      case "incomplete_expired" => IncompleteExpired
      case "paused" => Paused // Stripe has paused via pause_collection
      case "unpaid" => Unpaid
      case other => 
        throw new Exception(s"Unknown UserPricingPlanStatus: $other")
    }

  implicit val encoder: Encoder[UserPricingPlanStatus] =
    Encoder.encodeString.contramap {
      case Active => "Active"
      case Trialing => "Trialing"
      case PastDue => "PastDue"
      case Canceled => "Canceled"
      case Incomplete => "Incomplete"
      case IncompleteExpired => "IncompleteExpired"
      case Paused => "Paused"
      case Unpaid => "Unpaid"
    }

  implicit val decoder: Decoder[UserPricingPlanStatus] =
    Decoder.decodeString.emap {
      case "Active" => Right(Active)
      case "Trialing" => Right(Trialing)
      case "PastDue" => Right(PastDue)
      case "Canceled" => Right(Canceled)
      case "Incomplete" => Right(Incomplete)
      case "IncompleteExpired" => Right(IncompleteExpired)
      case "Paused" => Right(Paused)
      case "Unpaid" => Right(Unpaid)
      case other => Left(s"Invalid UserPricingPlanStatus: $other")
    }
}
