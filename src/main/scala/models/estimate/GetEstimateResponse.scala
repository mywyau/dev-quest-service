package models.estimate

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.EstimationStatus
import models.estimate.CalculatedEstimate

case class GetEstimateResponse(
  estimationStatus: EstimationStatus,
  calculatedEstimate: List[CalculatedEstimate]
)

object GetEstimateResponse {
  implicit val encoder: Encoder[GetEstimateResponse] = deriveEncoder[GetEstimateResponse]
  implicit val decoder: Decoder[GetEstimateResponse] = deriveDecoder[GetEstimateResponse]
}
