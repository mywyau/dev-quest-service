package services

import cats.Monad
import cats.NonEmptyParallel
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import fs2.Stream
import models.UserType
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.skills.Skill
import models.skills.SkillData
import models.users.*
import org.typelevel.log4cats.Logger
import repositories.SkillDataRepositoryAlgebra

import java.util.UUID

trait SkillDataServiceAlgebra[F[_]] {

  def getHiscoreSkillData(skill: Skill): F[List[SkillData]]
}

class SkillDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  skillRepo: SkillDataRepositoryAlgebra[F]
) extends SkillDataServiceAlgebra[F] {

  override def getHiscoreSkillData(skill: Skill): F[List[SkillData]] =
    skillRepo.getHiscoreSkillData(skill).map(_.sortBy(_.xp)(Ordering[BigDecimal].reverse))

}

object SkillDataService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](skillRepo: SkillDataRepositoryAlgebra[F]): SkillDataServiceAlgebra[F] =
    new SkillDataServiceImpl[F](skillRepo)
}
