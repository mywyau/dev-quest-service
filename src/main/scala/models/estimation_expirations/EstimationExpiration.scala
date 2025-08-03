package models.estimation_expirations

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.Instant

case class EstimationExpiration(
  estimationCloseAt: Option[Instant]
)

object EstimationExpiration {
  implicit val encoder: Encoder[EstimationExpiration] = deriveEncoder[EstimationExpiration]
  implicit val decoder: Decoder[EstimationExpiration] = deriveDecoder[EstimationExpiration]
}
