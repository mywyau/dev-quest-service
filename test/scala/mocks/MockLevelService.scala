package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.languages.*
import models.quests.*
import models.skills.*
import models.users.*
import services.LevelServiceAlgebra

case object MockLevelService extends LevelServiceAlgebra[IO] {

  override def calculateLevel(xp: BigDecimal): Int = ???

  override def awardSkillXpWithLevel(
    devId: String,
    username: String,
    skill: Skill,
    xpToAdd: BigDecimal
  ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def awardLanguageXpWithLevel(
    devId: String,
    username: String,
    language: Language,
    xpToAdd: BigDecimal
  ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
}
