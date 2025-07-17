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
import models.users.*
import org.typelevel.log4cats.Logger
import repositories.UserDataRepositoryAlgebra

import java.util.UUID

trait UserDataServiceAlgebra[F[_]] {

  def getUser(userId: String): F[Option[UserData]]

  def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateUserData(userId: String, updateUserData: UpdateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteUser(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class UserDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  userRepo: UserDataRepositoryAlgebra[F]
) extends UserDataServiceAlgebra[F] {

  override def getUser(userId: String): F[Option[UserData]] =
    userRepo.findUser(userId).flatMap {
      case Some(user) =>
        Logger[F].debug(s"[UserDataService] Found user with ID: $userId") *> Concurrent[F].pure(Some(user))
      case None =>
        Logger[F].debug(s"[UserDataService] No user found with ID: $userId") *> Concurrent[F].pure(None)
    }

  override def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    Logger[F].debug(s"[UserDataService] Creating a new user for userId: $userId") *>
      userRepo.createUser(userId, createUserData).flatMap {
        case Valid(value) =>
          Logger[F].debug(s"[UserDataService] User successfully created with ID: $userId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[UserDataService] Failed to create user. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }

  override def updateUserData(userId: String, updateUserData: UpdateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userRepo.updateUserData(userId, updateUserData).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[UserDataService][updateUserData] Successfully updated user with ID: $userId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[UserDataService][updateUserData] Failed to update user with ID: $userId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }

  override def deleteUser(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userRepo.deleteUser(userId).flatMap {
      case Valid(value) =>
        Logger[F].debug(s"[UserDataService] Successfully deleted user with ID: $userId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[UserDataService] Failed to delete user with ID: $userId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }
}

object UserDataService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    userRepo: UserDataRepositoryAlgebra[F]
  ): UserDataServiceAlgebra[F] =
    new UserDataServiceImpl[F](userRepo)
}
