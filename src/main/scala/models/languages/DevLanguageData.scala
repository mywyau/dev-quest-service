package models.languages

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.languages.Language

case class DevLanguageData(
  devId: String,
  username: String,
  language: Language,
  level: Int,
  xp: BigDecimal,  
  nextLevel: Int,
  nextLevelXp: BigDecimal
)

object DevLanguageData {
  implicit val encoder: Encoder[DevLanguageData] = deriveEncoder[DevLanguageData]
  implicit val decoder: Decoder[DevLanguageData] = deriveDecoder[DevLanguageData]
}
