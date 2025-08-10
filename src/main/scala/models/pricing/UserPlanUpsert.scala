package models.pricing

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.Instant
import java.time.LocalDateTime

case class UserPlanUpsert(
  userId: String,
  planId: String,
  stripeSubscriptionId: Option[String],
  stripeCustomerId: Option[String],
  status: UserPricingPlanStatus,
  currentPeriodEnd: Option[Instant]
  // name: String,
  // description: Option[String],
  // price: BigDecimal,
  // interval: String,
  // isActive: Boolean,

  // stripePriceId: Option[String],
  // features: Json,
  // sortOrder: Int,
)

object UserPlanUpsert {
  implicit val encoder: Encoder[UserPlanUpsert] = deriveEncoder[UserPlanUpsert]
  implicit val decoder: Decoder[UserPlanUpsert] = deriveDecoder[UserPlanUpsert]
}
