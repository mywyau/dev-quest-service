package models.profile

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import models.skills.Skill

case class ProfileSkillData(
  skill: Skill,
  skillLevel: Int,
  skillXp: BigDecimal
)

object ProfileSkillData {
  implicit val encoder: Encoder[ProfileSkillData] = deriveEncoder[ProfileSkillData]
  implicit val decoder: Decoder[ProfileSkillData] = deriveDecoder[ProfileSkillData]
}
