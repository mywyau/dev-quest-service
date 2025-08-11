package repositories

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.database.*
import models.languages.*
import org.typelevel.log4cats.Logger
import services.LevelServiceAlgebra

import java.sql.Timestamp
import java.time.LocalDateTime

trait UserPricingPlanRepositoryAlgebra[F[_]] {

  // def upsert(up: UserPlanUpsert): F[Unit]
  // def get(userId: String): F[Option[UserPlanView]] 
  // def setStatus(userId: String, status: String, currEnd: Option[Instant], cancelAtPeriodEnd: Boolean): F[Unit]

}

class UserPricingPlanRepositoryImpl[F[_] : Concurrent : Monad : Logger](
  transactor: Transactor[F]
) extends UserPricingPlanRepositoryAlgebra[F] {

  implicit val languageMeta: Meta[Language] = Meta[String].timap(Language.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)
}

object UserPricingPlanRepository {
  def apply[F[_] : Concurrent : Monad : Logger](
    transactor: Transactor[F]
  ): UserPricingPlanRepositoryAlgebra[F] =
    new UserPricingPlanRepositoryImpl[F](transactor)
}
