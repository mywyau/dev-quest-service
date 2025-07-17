package models.profile

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.languages.Language
import models.languages.LanguageData
import models.skills.Skill

case class DevProfileData(
  devId: String,
  username: String,
  email: String,
  mobile: String,
  skillData: List[ProfileSkillData],
  languageData: List[ProfileLanguageData]
)

object DevProfileData {
  implicit val encoder: Encoder[DevProfileData] = deriveEncoder[DevProfileData]
  implicit val decoder: Decoder[DevProfileData] = deriveDecoder[DevProfileData]
}
