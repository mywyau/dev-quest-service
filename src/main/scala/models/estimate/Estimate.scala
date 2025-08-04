package models.estimate

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime
import models.Rank

case class Estimate(
  devId: String,
  username: String,
  score: Int,
  hours: BigDecimal,
  comment: Option[String]
)

object Estimate {
  implicit val encoder: Encoder[Estimate] = deriveEncoder[Estimate]
  implicit val decoder: Decoder[Estimate] = deriveDecoder[Estimate]
}
