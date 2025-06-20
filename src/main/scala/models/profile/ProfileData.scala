package models.profile

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.languages.Language
import models.languages.LanguageData
import models.skills.Skill

case class ProfileData(
  devId: String,
  username: Option[String],
  skillData: List[ProfileSkillData],
  languageData: List[ProfileLanguageData]
)

object ProfileData {
  implicit val encoder: Encoder[ProfileData] = deriveEncoder[ProfileData]
  implicit val decoder: Decoder[ProfileData] = deriveDecoder[ProfileData]
}
