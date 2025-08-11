package models.pricing

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime

case class PricingPlanRow(
  planId: String,
  name: String,
  description: Option[String],
  price: BigDecimal,
  interval: String,
  isActive: Boolean,
  stripePriceId: Option[String],
  features: Json,
  sortOrder: Int,
  // createdAt: LocalDateTime
)

object PricingPlanRow {
  implicit val encoder: Encoder[PricingPlanRow] = deriveEncoder[PricingPlanRow]
  implicit val decoder: Decoder[PricingPlanRow] = deriveDecoder[PricingPlanRow]
}
