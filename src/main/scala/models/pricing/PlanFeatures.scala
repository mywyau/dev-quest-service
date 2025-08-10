// models/pricing/PlanFeatures.scala
package models.pricing

import io.circe.*
import io.circe.generic.semiauto.*

// Simple union of both client/dev keys; make optional to handle both kinds of plans
final case class PlanFeatures(
  maxActiveQuests: Option[Int],
  devPool: Option[String],
  estimations: Option[Boolean],
  canCustomizeLevelThresholds: Option[Boolean],
  boostQuests: Option[Boolean],
  showOnLeaderBoard: Option[Boolean],
  communicateWithClient: Option[Boolean]
)

object PlanFeatures {
  given Encoder[PlanFeatures] = deriveEncoder
  given Decoder[PlanFeatures] = deriveDecoder
}
