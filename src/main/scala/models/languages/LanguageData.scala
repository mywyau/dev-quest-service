package models.languages

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.languages.Language

case class LanguageData(
  devId: String,
  language: Language,
  level: Int,
  xp: BigDecimal
)

object LanguageData {
  implicit val encoder: Encoder[LanguageData] = deriveEncoder[LanguageData]
  implicit val decoder: Decoder[LanguageData] = deriveDecoder[LanguageData]
}
