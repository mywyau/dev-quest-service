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
import repositories.SkillDataRepositoryAlgebra

trait SkillDataServiceAlgebra[F[_]] {

  def getSkillData(devId: String, skill: Skill): F[Option[SkillData]]

}

class SkillDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  skillRepo: SkillDataRepositoryAlgebra[F]
) extends SkillDataServiceAlgebra[F] {

  override def getSkillData(devId: String, skill: Skill): F[Option[SkillData]] =
    skillRepo.getSkillData(devId, skill).flatMap {
      case Some(skill) =>
        Logger[F].info(s"[SkillDataService] Found reviewing skill data for user with devId: $devId") *>
          Concurrent[F].pure(Some(skill))
      case None =>
        Logger[F].info(s"[SkillDataService] No reviewing skill data found for user with devId: $devId") *>
          Concurrent[F].pure(None)
    }

}

object SkillDataService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](skillRepo: SkillDataRepositoryAlgebra[F]): SkillDataServiceAlgebra[F] =
    new SkillDataServiceImpl[F](skillRepo)
}
