package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.QuestStatus
import models.database.*
import models.database.CreateSuccess
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.quests.*
import models.skills.Skill
import models.skills.SkillData
import repositories.SkillDataRepositoryAlgebra

case object MockSkillDataRepository extends SkillDataRepositoryAlgebra[IO] {

  def getAllSkillData(): IO[List[SkillData]] = ???

  override def getAllSkills(devId: String): IO[List[SkillData]] = ???

  override def getSkill(devId: String, skill: Skill): IO[Option[SkillData]] = ???

  override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = ???

  override def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal, level: Int): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
