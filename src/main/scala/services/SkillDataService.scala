package services

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import fs2.Stream
import java.util.UUID
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.skills.Skill
import models.skills.SkillData
import models.users.*
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.DevSkillRepositoryAlgebra

trait SkillDataServiceAlgebra[F[_]] {

  def countForSkill(skill: Skill): F[Int]

  def getHiscoreSkillData(skill: Skill): F[List[SkillData]]

  def getPaginatedSkillData(skill: Skill, offset: Int, limit: Int): F[List[SkillData]]

}

class SkillDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  skillRepo: DevSkillRepositoryAlgebra[F]
) extends SkillDataServiceAlgebra[F] {

  override def countForSkill(skill: Skill): F[Int] =
    skillRepo.countForSkill(skill).flatMap { numberOfQuests =>
      Logger[F].debug(s"[LevelService][countForLanguage] Total number of entries found for Skill: $skill") *>
        Concurrent[F].pure(numberOfQuests)
    }

  override def getHiscoreSkillData(skill: Skill): F[List[SkillData]] =
    skillRepo.getHiscoreSkillData(skill).map(_.sortBy(_.xp)(Ordering[BigDecimal].reverse))


  override def getPaginatedSkillData(skill: Skill, offset: Int, limit: Int): F[List[SkillData]] = 
    skillRepo.getPaginatedSkillData(skill, offset, limit)
}

object SkillDataService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](skillRepo: DevSkillRepositoryAlgebra[F]): SkillDataServiceAlgebra[F] =
    new SkillDataServiceImpl[F](skillRepo)
}
