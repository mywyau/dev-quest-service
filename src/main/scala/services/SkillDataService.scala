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
import models.skills.SkillData
import models.users.*
import org.typelevel.log4cats.Logger
import repositories.SkillDataRepositoryAlgebra

import java.util.UUID

trait SkillDataServiceAlgebra[F[_]] {

  def getReviewingSkillData(devId: String): F[Option[SkillData]]

  def getQuestingSkillData(devId: String): F[Option[SkillData]]

  def getTestingSkillData(devId: String): F[Option[SkillData]]

}

class SkillDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  skillRepo: SkillDataRepositoryAlgebra[F]
) extends SkillDataServiceAlgebra[F] {

  override def getReviewingSkillData(devId: String): F[Option[SkillData]] =
    skillRepo.getReviewingSkillData(devId).flatMap {
      case Some(user) =>
        Logger[F].info(s"[SkillDataService] Found reviewing skill data for user with devId: $devId") *>
          Concurrent[F].pure(Some(user))
      case None =>
        Logger[F].info(s"[SkillDataService] No reviewing skill data found for user with devId: $devId") *>
          Concurrent[F].pure(None)
    }

  override def getQuestingSkillData(devId: String): F[Option[SkillData]] =
    skillRepo.getQuestingSkillData(devId).flatMap {
      case Some(user) =>
        Logger[F].info(s"[SkillDataService] Found questing skill data for user with devId: $devId") *>
          Concurrent[F].pure(Some(user))
      case None =>
        Logger[F].info(s"[SkillDataService] No questing skill data found for user with devId: $devId") *>
          Concurrent[F].pure(None)
    }

  override def getTestingSkillData(devId: String): F[Option[SkillData]] =
    skillRepo.getTestingSkillData(devId).flatMap {
      case Some(user) =>
        Logger[F].info(s"[SkillDataService] Found questing skill data for user with devId: $devId") *>
          Concurrent[F].pure(Some(user))
      case None =>
        Logger[F].info(s"[SkillDataService] No questing skill data found for user with devId: $devId") *>
          Concurrent[F].pure(None)
    }

}

object SkillDataService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](skillRepo: SkillDataRepositoryAlgebra[F]): SkillDataServiceAlgebra[F] =
    new SkillDataServiceImpl[F](skillRepo)
}
