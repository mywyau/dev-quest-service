package models.skills

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.skills.Skill

case class DevSkillData(
  devId: String,
  username: String,
  skill: Skill,
  level: Int,
  xp: BigDecimal,
  nextLevel: Int,
  nextLevelXp: BigDecimal
)

object DevSkillData {
  implicit val encoder: Encoder[DevSkillData] = deriveEncoder[DevSkillData]
  implicit val decoder: Decoder[DevSkillData] = deriveDecoder[DevSkillData]
}
