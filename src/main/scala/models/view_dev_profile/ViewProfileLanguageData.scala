package models.view_dev_profile

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.languages.Language

case class ViewProfileLanguageData(
  language: Language,
  languageLevel: Int,
)

object ViewProfileLanguageData {
  implicit val encoder: Encoder[ViewProfileLanguageData] = deriveEncoder[ViewProfileLanguageData]
  implicit val decoder: Decoder[ViewProfileLanguageData] = deriveDecoder[ViewProfileLanguageData]
}
