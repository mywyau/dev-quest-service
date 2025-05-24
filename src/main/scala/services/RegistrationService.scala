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
import models.UserType
import org.typelevel.log4cats.Logger
import models.users.CreateUserData
import repositories.UserDataRepositoryImpl
import repositories.UserDataRepositoryAlgebra

trait RegistrationServiceAlgebra[F[_]] {

  def createUser(userId: String, createRegistration: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class RegistrationServiceImpl[F[_] : Concurrent : Monad : Logger](
  userDataRepo: UserDataRepositoryAlgebra[F]
) extends RegistrationServiceAlgebra[F] {

  override def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    Logger[F].info(s"[RegistrationService] Creating a new user for user: $userId") *>
      userDataRepo.createUser(userId, createUserData).flatMap {
        case Valid(value) =>
          Logger[F].info(s"[RegistrationService] User successfully created with ID: $userId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[RegistrationService] Failed to create user. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }
}

object RegistrationService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    userDataRepo: UserDataRepositoryAlgebra[F]
  ): RegistrationServiceAlgebra[F] =
    new RegistrationServiceImpl[F](userDataRepo)
}
