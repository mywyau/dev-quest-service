package services

import cats.data.ValidatedNel
import cats.effect.Sync
import cats.effect.kernel.Async
import cats.effect.kernel.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import models.auth.UserSession.decoder
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

  def countTotalUsers(): F[Int]

  def calculateLevel(xp: BigDecimal): Int

  def getTotalLevelHiscores(): F[List[TotalLevel]]

  def getPaginatedTotalLevelHiscores(offset: Int, limit: Int): F[List[TotalLevel]]

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

class LevelServiceImpl[F[_] : Concurrent : Logger](
  skillDataRepository: SkillDataRepositoryAlgebra[F],
  languageDataRepository: LanguageRepositoryAlgebra[F]
) extends LevelServiceAlgebra[F] {

  private def generateLevelThresholds(): Vector[Int] = {
    val maxLevel = 99
    val totalXP = 15000000
    val linearLevels = 90 // one less than before to leave room for 9 curved levels
    val linearBudget = totalXP * 0.7
    val curveLevels = maxLevel - linearLevels
    val curveBudget = totalXP * 0.3

    val linearGains: Vector[Double] = {
      val raw = (1 to linearLevels).map(_ * 1000.0).toVector
      val scale = linearBudget / raw.sum
      raw.map(_ * scale)
    }

    val linearThresholds = linearGains.scanLeft(0.0)(_ + _).drop(1)

    val curveGains: Vector[Double] = {
      val base = 100000.0
      val exponent = 1.3
      val raw = (1 to curveLevels).map(i => Math.pow(base + i * 10000, exponent)).toVector
      val scale = curveBudget / raw.sum
      raw.map(_ * scale)
    }

    val curveThresholds: Vector[Double] = curveGains.scanLeft(linearThresholds.last)(_ + _).drop(1)

    // Combine both parts and start at level 1 (0 XP for level 1)
    ((0.0 +: linearThresholds) ++ curveThresholds).take(99).map(_.toInt)
  }

  val levelThresholds: Vector[Int] = generateLevelThresholds()

  override def countTotalUsers(): F[Int] =
    getTotalLevelHiscores().map(_.size)

  override def calculateLevel(xp: BigDecimal): Int =
    levelThresholds.lastIndexWhere(threshold => xp >= threshold) + 1

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

  override def getPaginatedTotalLevelHiscores(offset: Int, limit: Int): F[List[TotalLevel]] =
    getTotalLevelHiscores().map(_.slice(offset, offset + limit))

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
      // 👇 Place the new level calculation logic here
      nextLevel = newLevel + 1
      nextLevelXp = levelThresholds.lift(newLevel).getOrElse(levelThresholds.last)

      result <- skillDataRepository.awardSkillXP(devId, username, skill, newTotalXp, newLevel, nextLevel, nextLevelXp)
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
      nextLevel = newLevel + 1
      nextLevelXp = levelThresholds.lift(newLevel).getOrElse(levelThresholds.last)
      result <- languageDataRepository.awardLanguageXP(devId, username, language, newTotalXp, newLevel, nextLevel, nextLevelXp)
    } yield result
}

object LevelService {

  def apply[F[_] : Async : Logger](
    skillRepo: SkillDataRepositoryAlgebra[F],
    languageRepo: LanguageRepositoryAlgebra[F]
  ): LevelServiceAlgebra[F] = new LevelServiceImpl[F](skillRepo, languageRepo)
}
