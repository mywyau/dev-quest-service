package models.pricing

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime

case class UserPricingPlanRow(
  userId: String,
  planId: String,
  stripeSubscriptionId: Option[String],
  stripeCustomerId: Option[String],
  status: UserPricingPlanStatus,
  startedAt: Instant,
  currentPeriodEnd: Option[Instant],
  cancelAtPeriodEnd: Boolean
)

object UserPricingPlanRow {
  implicit val encoder: Encoder[UserPricingPlanRow] = deriveEncoder[UserPricingPlanRow]
  implicit val decoder: Decoder[UserPricingPlanRow] = deriveDecoder[UserPricingPlanRow]
}
