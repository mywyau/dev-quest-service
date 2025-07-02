package models.estimate

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.EstimationStatus
import models.Rank

case class CalculatedEstimate(
  username: String,
  score: Int,
  days: Int,
  rank: Rank,
  comment: Option[String]
)

object CalculatedEstimate {
  implicit val encoder: Encoder[CalculatedEstimate] = deriveEncoder[CalculatedEstimate]
  implicit val decoder: Decoder[CalculatedEstimate] = deriveDecoder[CalculatedEstimate]
}
