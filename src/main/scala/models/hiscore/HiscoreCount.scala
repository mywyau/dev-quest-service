package models.hiscore

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class HiscoreCount(numberOfDevs:Int)

object HiscoreCount {
  implicit val encoder: Encoder[HiscoreCount] = deriveEncoder[HiscoreCount]
  implicit val decoder: Decoder[HiscoreCount] = deriveDecoder[HiscoreCount]
}