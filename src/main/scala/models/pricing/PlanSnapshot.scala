package models.pricing

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime
import scala.util.Try

// Snapshot for cache / feature gates (small, fast, serializable)
final case class PlanSnapshot(
  userId: String,
  planId: String,
  status: UserPricingPlanStatus,
  features: PlanFeatures,
  currentPeriodEnd: Option[Instant],
  cancelAtPeriodEnd: Boolean
)

object PlanSnapshot:
  given Encoder[PlanSnapshot] = deriveEncoder
  given Decoder[PlanSnapshot] = deriveDecoder
