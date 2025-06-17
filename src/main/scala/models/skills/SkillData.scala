package models.skills

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.skills.Skill

case class SkillData(
  devId: String,
  skill: Skill,
  level: Int,
  xp: BigDecimal
)

object SkillData {
  implicit val encoder: Encoder[SkillData] = deriveEncoder[SkillData]
  implicit val decoder: Decoder[SkillData] = deriveDecoder[SkillData]
}
