package services

import cats.data.ValidatedNel
import cats.effect.kernel.Async
import cats.effect.Sync
import cats.implicits.*
import cats.syntax.all.*
import models.database.*
import models.hiscore.TotalLevel
import models.languages.Language
import models.languages.LanguageData
import models.skills.Skill
import models.skills.SkillData
import org.typelevel.log4cats.Logger
import repositories.LanguageRepository
import repositories.LanguageRepositoryAlgebra
import repositories.SkillDataRepository
import repositories.SkillDataRepositoryAlgebra

trait LevelServiceAlgebra[F[_]] {

  def calculateLevel(xp: BigDecimal): Int

  def getTotalLevelHiscores(): F[List[TotalLevel]]

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
    val a = 1500.0
    val b = 1.100

    val level = Math.log((xp.toDouble + a) / a) / Math.log(b) + 1
    Math.min(level.toInt, 120)
  }

  override def getTotalLevelHiscores(): F[List[TotalLevel]] =
    for {
      skillData: List[SkillData] <- skillDataRepository.getAllSkillData()
      languageData: List[LanguageData] <- languageDataRepository.getAllLanguageData()

      // Group and sum skill data
      skillTotals = skillData
        .groupBy(_.devId)
        .view
        .mapValues { entries =>
          val username = entries.headOption.map(_.username).getOrElse("unknown")
          val level = entries.map(_.level).sum
          val xp = entries.map(_.xp).combineAll // uses cats syntax
          (username, level, xp)
        }
        .toMap

      // Group and sum language data
      languageTotals = languageData
        .groupBy(_.devId)
        .view
        .mapValues { entries =>
          val username = entries.headOption.map(_.username).getOrElse("unknown")
          val level = entries.map(_.level).sum
          val xp = entries.map(_.xp).combineAll
          (username, level, xp)
        }
        .toMap

      // Combine both maps
      allDevIds = (skillTotals.keySet ++ languageTotals.keySet).toList

      combined = allDevIds.map { devId =>
        val (username1, level1, xp1) = skillTotals.getOrElse(devId, ("unknown", 0, BigDecimal(0)))
        val (username2, level2, xp2) = languageTotals.getOrElse(devId, ("unknown", 0, BigDecimal(0)))

        TotalLevel(
          devId = devId,
          username = if (username1 != "unknown") username1 else username2,
          totalLevel = level1 + level2,
          totalXP = xp1 + xp2
        )
      }

    } yield combined.sortBy(-_.totalXP.toDouble) // sort descending by XP

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
