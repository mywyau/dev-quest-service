package repositories

import cats.Monad
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.UserType
import models.database.*
import models.database.ConstraintViolation
import models.database.CreateSuccess
import models.database.DataTooLongError
import models.database.DatabaseConnectionError
import models.database.DatabaseError
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.database.DeleteSuccess
import models.database.ForeignKeyViolationError
import models.database.NotFoundError
import models.database.SqlExecutionError
import models.database.UnexpectedResultError
import models.database.UnknownError
import models.database.UpdateSuccess
import models.stripe.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.LocalDateTime

trait StripeAccountRepositoryAlgebra[F[_]] {

  def findStripeAccount(devUserId: String): F[Option[StripeAccountDetails]]

  def saveStripeAccountId(devUserId: String, accountId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateStripeAccountStatus(devUserId: String, status: StripeAccountStatus): F[Unit]

}

class StripeAccountRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends StripeAccountRepositoryAlgebra[F] {

  implicit val userMeta: Meta[UserType] = Meta[String].timap(UserType.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def findStripeAccount(devUserId: String): F[Option[StripeAccountDetails]] = {
    val findQuery: F[Option[StripeAccountDetails]] =
      sql"""
         SELECT 
            stripe_account_id,
            onboarded,
            charges_enabled,
            payouts_enabled
         FROM stripe_accounts
         WHERE user_id = $devUserId
       """.query[StripeAccountDetails].option.transact(transactor)

    findQuery
  }

  override def saveStripeAccountId(devUserId: String, accountId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    sql"""
    INSERT INTO stripe_accounts (
      user_id,
      stripe_account_id
    )
    VALUES (
      $devUserId,
      $accountId
    )
  """.update.run
      .transact(transactor)
      .attempt
      .map {
        case Right(affectedRows) if affectedRows == 1 =>
          CreateSuccess.validNel
        case Left(e: java.sql.SQLIntegrityConstraintViolationException) =>
          ConstraintViolation.invalidNel
        case Left(e: java.sql.SQLException) =>
          DatabaseError.invalidNel
        case Left(ex) =>
          UnknownError(s"Unexpected error: ${ex.getMessage}").invalidNel
        case _ =>
          UnexpectedResultError.invalidNel
      }

  def updateStripeAccountStatus(devId: String, status: StripeAccountStatus): F[Unit] =
    sql"""
    UPDATE stripe_accounts
    SET
      charges_enabled = ${status.chargesEnabled},
      payouts_enabled = ${status.payoutsEnabled},
      details_submitted = ${status.detailsSubmitted},
      updated_at = CURRENT_TIMESTAMP
    WHERE stripe_account_id = ${status.stripeAccountId} AND userId = $devId
  """.update.run.transact(transactor).void
}

object StripeAccountRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): StripeAccountRepositoryAlgebra[F] =
    new StripeAccountRepositoryImpl[F](transactor)
}
