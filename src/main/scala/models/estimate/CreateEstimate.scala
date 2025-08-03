package models.estimate

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.Rank

import java.time.LocalDateTime

case class CreateEstimate(
  questId: String,
  score: Int,
  hours: Int,
  comment: Option[String]
)

object CreateEstimate {
  implicit val encoder: Encoder[CreateEstimate] = deriveEncoder[CreateEstimate]
  implicit val decoder: Decoder[CreateEstimate] = deriveDecoder[CreateEstimate]
}
