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
import models.users.CreateUserData
import models.users.UserData
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.UserDataRepositoryAlgebra
import repositories.UserDataRepositoryImpl
import models.users.Registration

trait RegistrationServiceAlgebra[F[_]] {

  def getUser(userId: String): F[Option[UserData]]

  def createUser(userId: String, createRegistration: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def registerUser(userId: String, userType: Registration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class RegistrationServiceImpl[F[_] : Concurrent : Monad : Logger](
  userDataRepo: UserDataRepositoryAlgebra[F]
) extends RegistrationServiceAlgebra[F] {

  override def getUser(userId: String): F[Option[UserData]] =
    userDataRepo.findUser(userId).flatMap {
      case Some(user) =>
        Logger[F].debug(s"[UserDataService] Found user with ID: $userId") *> Concurrent[F].pure(Some(user))
      case None =>
        Logger[F].debug(s"[UserDataService] No user found with ID: $userId") *> Concurrent[F].pure(None)
    }

  override def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = {

    val createUserWithLogging =
      userDataRepo.createUser(userId, createUserData).flatMap {
        case Valid(value) =>
          Logger[F].debug(s"[RegistrationService] User successfully created with ID: $userId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[RegistrationService] Failed to create user. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }

    Logger[F].debug(s"[RegistrationService] Attempting to create a new user for userId: $userId") *>
      userDataRepo.findUserNoUserName(userId).flatMap {
        case None =>
          createUserWithLogging
        case Some(value) =>
          Logger[F].debug(s"[RegistrationService] User already created with ID: ${value.userId}") *>
            Concurrent[F].pure(Valid(ReadSuccess(value)))
      }
  }

  override def registerUser(userId: String, userType: Registration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userDataRepo.registerUser(userId, userType).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[UserDataService][update] Successfully updated user with ID: $userId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[UserDataService][update] Failed to update user with ID: $userId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }
}

object RegistrationService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    userDataRepo: UserDataRepositoryAlgebra[F]
  ): RegistrationServiceAlgebra[F] =
    new RegistrationServiceImpl[F](userDataRepo)
}
