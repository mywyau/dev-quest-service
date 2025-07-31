package models.work_time

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class HoursOfWork(
    hoursOfWork: Double                    
)

object HoursOfWork {
  implicit val encoder: Encoder[HoursOfWork] = deriveEncoder[HoursOfWork]
  implicit val decoder: Decoder[HoursOfWork] = deriveDecoder[HoursOfWork]
}
