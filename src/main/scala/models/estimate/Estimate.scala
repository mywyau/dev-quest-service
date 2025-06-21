package models.estimate

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.Rank

import java.time.LocalDateTime

case class Estimate(
  username: String,
  rank: Rank,
  comment: Option[String]
)

object Estimate {
  implicit val encoder: Encoder[Estimate] = deriveEncoder[Estimate]
  implicit val decoder: Decoder[Estimate] = deriveDecoder[Estimate]
}
