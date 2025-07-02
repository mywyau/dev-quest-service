package models.estimate

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.Rank

case class Estimate(
  username: String,
  score: Int,
  days: Int,
  comment: Option[String]
)

object Estimate {
  implicit val encoder: Encoder[Estimate] = deriveEncoder[Estimate]
  implicit val decoder: Decoder[Estimate] = deriveDecoder[Estimate]
}
