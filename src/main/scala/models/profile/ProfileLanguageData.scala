package models.profile

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.languages.Language

case class ProfileLanguageData(
  language: Language,
  languageLevel: Int,
  languageXp: BigDecimal,
  nextLevel: Int,
  nextLevelXp: BigDecimal
)

object ProfileLanguageData {
  implicit val encoder: Encoder[ProfileLanguageData] = deriveEncoder[ProfileLanguageData]
  implicit val decoder: Decoder[ProfileLanguageData] = deriveDecoder[ProfileLanguageData]
}
