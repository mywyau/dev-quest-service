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
  stripeSubscriptionId: String,
  stripeCustomerId: String,
  status: UserPricingPlanStatus,
  started_at: LocalDateTime,
  currentPeriodEnd: LocalDateTime,
  cancelAtPeriodEnd: Boolean
)

object UserPricingPlanRow {
  implicit val encoder: Encoder[UserPricingPlanRow] = deriveEncoder[UserPricingPlanRow]
  implicit val decoder: Decoder[UserPricingPlanRow] = deriveDecoder[UserPricingPlanRow]
}
