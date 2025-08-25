package services

import cats.data.ValidatedNel
import cats.effect.Sync
import cats.syntax.all.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.skills.Skill
import models.Rank
import org.typelevel.log4cats.Logger
import models.kafka.QuestEstimationFinalized
import models.skills.Estimating

trait XpServiceAlgebra[F[_]] {
  
  def awardXp(
    devId: String,
    username: String,
    skill: Skill,
    baseXp: Double,
    modifier: BigDecimal,
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def awardEstimationXp(event: QuestEstimationFinalized): F[Unit] 
}

class XpServiceImpl[F[_] : Sync : Logger](
  levelService: LevelServiceAlgebra[F]
) extends XpServiceAlgebra[F] {

  def calculateXp(baseXp: Double, modifier: BigDecimal): Int =
    (BigDecimal(baseXp) * (1 + modifier)).toInt.max(0)

  override def awardXp(
    devId: String,
    username: String,
    skill: Skill,
    baseXp: Double,
    modifier: BigDecimal,
    rank: Rank
  ): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val xp = calculateXp(baseXp, modifier)

    Logger[F]
      .info(s"[XpService] Awarding $xp XP to $username (devId=$devId) for skill $skill | Rank=$rank | Base=$baseXp | Mod=$modifier") *>
      levelService.awardSkillXpWithLevel(devId, username, skill, xp)
  }

  override def awardEstimationXp(event: QuestEstimationFinalized): F[Unit] =
    event.userEstimates.traverse_ { estimate =>
      val xp = (event.baseXp * (1 + estimate.modifier)).toInt.max(0)
      Logger[F].info(
        s"Awarding $xp XP to ${estimate.username} (devId=${estimate.devId}) for quest ${event.questId}"
      ) *>
        levelService.awardSkillXpWithLevel(
          devId = estimate.devId,
          username = estimate.username,
          skill = Estimating,
          xpToAdd = xp
        )
    }
}
