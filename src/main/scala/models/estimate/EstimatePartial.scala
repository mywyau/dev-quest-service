package models.estimate

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.Rank

import java.time.LocalDateTime

case class EstimatePartial(
  username: String,
  rank: Rank,
  comment: Option[String]
)

object EstimatePartial {
  implicit val encoder: Encoder[EstimatePartial] = deriveEncoder[EstimatePartial]
  implicit val decoder: Decoder[EstimatePartial] = deriveDecoder[EstimatePartial]
}
