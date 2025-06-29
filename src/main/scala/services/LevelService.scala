package services

import cats.data.ValidatedNel
import cats.effect.kernel.Async
import cats.effect.Sync
import cats.implicits.*
import cats.syntax.all.*
import models.database.*
import models.languages.Language
import models.skills.Skill
import org.typelevel.log4cats.Logger
import repositories.LanguageRepository
import repositories.LanguageRepositoryAlgebra
import repositories.SkillDataRepository
import repositories.SkillDataRepositoryAlgebra

trait LevelServiceAlgebra[F[_]] {

  def calculateLevel(xp: BigDecimal): Int

  def awardSkillXpWithLevel(
    devId: String,
    username: String,
    skill: Skill,
    xpToAdd: BigDecimal
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def awardLanguageXpWithLevel(
    devId: String,
    username: String,
    language: Language,
    xpToAdd: BigDecimal
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class LevelServiceImpl[F[_] : Async : Logger](
  skillDataRepository: SkillDataRepositoryAlgebra[F],
  languageDataRepository: LanguageRepositoryAlgebra[F]
) extends LevelServiceAlgebra[F] {

  override def calculateLevel(xp: BigDecimal): Int = {
    val level = Math.floor(Math.sqrt(xp.toDouble) / 10).toInt + 1
    Math.min(level, 99)
  }

  override def awardSkillXpWithLevel(
    devId: String,
    username: String,
    skill: Skill,
    xpToAdd: BigDecimal
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    for {
      maybeSkill <- skillDataRepository.getSkill(devId, skill)
      currentXp = maybeSkill.map(_.xp).getOrElse(BigDecimal(0))
      newTotalXp = currentXp + xpToAdd
      newLevel = calculateLevel(newTotalXp)
      result <- skillDataRepository.awardSkillXP(devId, username, skill, newTotalXp, newLevel)
    } yield result

  override def awardLanguageXpWithLevel(
    devId: String,
    username: String,
    language: Language,
    xpToAdd: BigDecimal
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    for {
      maybeSkill <- languageDataRepository.getLanguage(devId, language)
      currentXp = maybeSkill.map(_.xp).getOrElse(BigDecimal(0))
      newTotalXp = currentXp + xpToAdd
      newLevel = calculateLevel(newTotalXp)
      result <- languageDataRepository.awardLanguageXP(devId, username, language, newTotalXp, newLevel)
    } yield result
}

object LevelService {

  def apply[F[_] : Async : Logger](
    skillRepo: SkillDataRepositoryAlgebra[F],
    languageRepo: LanguageRepositoryAlgebra[F]
  ): LevelServiceAlgebra[F] = new LevelServiceImpl[F](skillRepo, languageRepo)
}
