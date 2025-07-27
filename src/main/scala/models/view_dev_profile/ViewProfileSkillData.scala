package models.view_dev_profile

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import models.skills.Skill

case class ViewProfileSkillData(
  skill: Skill,
  skillLevel: Int
)

object ViewProfileSkillData {
  implicit val encoder: Encoder[ViewProfileSkillData] = deriveEncoder[ViewProfileSkillData]
  implicit val decoder: Decoder[ViewProfileSkillData] = deriveDecoder[ViewProfileSkillData]
}
