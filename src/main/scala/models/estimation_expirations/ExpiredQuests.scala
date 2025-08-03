package models.estimation_expirations

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.Instant

case class ExpiredQuests(
  questId: String
)

object ExpiredQuests {
  implicit val encoder: Encoder[ExpiredQuests] = deriveEncoder[ExpiredQuests]
  implicit val decoder: Decoder[ExpiredQuests] = deriveDecoder[ExpiredQuests]
}
