package models.profile

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.languages.Language

case class ProfileLanguageData(
  language: Language,
  languageLevel: Int,
  languageXp: BigDecimal
)

object ProfileLanguageData {
  implicit val encoder: Encoder[ProfileLanguageData] = deriveEncoder[ProfileLanguageData]
  implicit val decoder: Decoder[ProfileLanguageData] = deriveDecoder[ProfileLanguageData]
}
