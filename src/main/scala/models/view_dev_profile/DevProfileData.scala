package models.view_dev_profile

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class DevProfileData(
  devId: String,
  username: String,
  email: String,
  mobile: String,
  skillData: List[ViewProfileSkillData],
  languageData: List[ViewProfileLanguageData]
)

object DevProfileData {
  implicit val encoder: Encoder[DevProfileData] = deriveEncoder[DevProfileData]
  implicit val decoder: Decoder[DevProfileData] = deriveDecoder[DevProfileData]
}
