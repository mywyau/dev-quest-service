package models.estimate

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.EstimationStatus
import models.Rank

case class EvaluatedEstimate(
  estimate: Estimate,
  modifier: BigDecimal
)

object EvaluatedEstimate {
  implicit val encoder: Encoder[EvaluatedEstimate] = deriveEncoder[EvaluatedEstimate]
  implicit val decoder: Decoder[EvaluatedEstimate] = deriveDecoder[EvaluatedEstimate]
}
