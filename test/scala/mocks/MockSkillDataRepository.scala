package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.database.CreateSuccess
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.quests.*
import models.skills.Skill
import models.QuestStatus
import repositories.SkillDataRepositoryAlgebra
import models.skills.SkillData

case object MockSkillDataRepository extends SkillDataRepositoryAlgebra[IO] {

  override def getAllSkills(devId: String): IO[List[SkillData]] = ???

  override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = ???

  override def awardSkillXP(devId: String, username: String, skill: Skill, xp: BigDecimal): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
