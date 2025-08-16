package models.pricing

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

final case class UserPricingPlanView(
  userPlanRow: UserPricingPlanRow,
  planRow: PricingPlanRow
)

object UserPricingPlanView {
  implicit val encoder: Encoder[UserPricingPlanView] = deriveEncoder[UserPricingPlanView]
  implicit val decoder: Decoder[UserPricingPlanView] = deriveDecoder[UserPricingPlanView]
}
