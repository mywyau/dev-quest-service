package models.pricing

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.Instant
import java.time.LocalDateTime
import models.UserType

case class PricingPlanRow(
  planId: String,
  name: String,
  description: Option[String],
  stripePriceId: Option[String],
  features: PlanFeatures,
  price: BigDecimal,
  interval: String,
  userType: UserType,
  createdAt: LocalDateTime  //this does not need to instant 
)

object PricingPlanRow {
  implicit val encoder: Encoder[PricingPlanRow] = deriveEncoder[PricingPlanRow]
  implicit val decoder: Decoder[PricingPlanRow] = deriveDecoder[PricingPlanRow]
}
