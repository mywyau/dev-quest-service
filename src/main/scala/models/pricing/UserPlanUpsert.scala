package models.pricing

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime

case class UserPlanUpsert(
  userId: String,
  planId: String,
  name: String,
  description: Option[String],
  price: BigDecimal,
  interval: String,
  isActive: Boolean,
  status: UserPricingPlanStatus,
  stripeCustomerId: Option[String],
  stripePriceId: Option[String],
  stripeSubscriptionId: Option[String],
  currentPeriodEnd: LocalDateTime,
  features: Json,
  sortOrder: Int,
)

object UserPlanUpsert {
  implicit val encoder: Encoder[UserPlanUpsert] = deriveEncoder[UserPlanUpsert]
  implicit val decoder: Decoder[UserPlanUpsert] = deriveDecoder[UserPlanUpsert]
}
