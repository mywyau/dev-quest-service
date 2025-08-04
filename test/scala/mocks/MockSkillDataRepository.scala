package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.QuestStatus
import models.database.*
import models.quests.*
import models.skills.*
import repositories.DevSkillRepositoryAlgebra

case object MockDevSkillRepository extends DevSkillRepositoryAlgebra[IO] {

  override def countForSkill(skill: Skill): IO[Int] = ???

  override def getPaginatedSkillData(skill: Skill, offset: Int, limit: Int): IO[List[SkillData]] = ???

  override def getSkillsForUser(username: String): IO[List[SkillData]] = ???

  def getAllSkillData(): IO[List[SkillData]] = ???

  override def getAllSkills(devId: String): IO[List[DevSkillData]] = ???

  override def getSkill(devId: String, skill: Skill): IO[Option[SkillData]] = ???

  override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = ???

  override def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal, level: Int, nextLevel: Int, nextLevelXp: BigDecimal): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
