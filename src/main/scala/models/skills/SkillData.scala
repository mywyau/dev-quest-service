package models.skills

package models.rewards

import models.skills.Skill
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import java.time.LocalDateTime

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
