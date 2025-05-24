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
import models.users.*
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.UserDataRepositoryAlgebra

trait UserDataServiceAlgebra[F[_]] {

  def getUser(userId: String): F[Option[UserData]]

  def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def updateUserType(userId: String, userType: UserType): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def deleteUser(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class UserDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  userRepo: UserDataRepositoryAlgebra[F]
) extends UserDataServiceAlgebra[F] {

  override def getUser(userId: String): F[Option[UserData]] =
    userRepo.findUser(userId).flatMap {
      case Some(user) =>
        Logger[F].info(s"[UserDataService] Found user with ID: $userId") *> Concurrent[F].pure(Some(user))
      case None =>
        Logger[F].info(s"[UserDataService] No user found with ID: $userId") *> Concurrent[F].pure(None)
    }

  override def createUser(userId: String, createUserData: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    Logger[F].info(s"[UserDataService] Creating a new user for userId: $userId") *>
      userRepo.createUser(userId, createUserData).flatMap {
        case Valid(value) =>
          Logger[F].info(s"[UserDataService] User successfully created with ID: $userId") *>
            Concurrent[F].pure(Valid(value))
        case Invalid(errors) =>
          Logger[F].error(s"[UserDataService] Failed to create user. Errors: ${errors.toList.mkString(", ")}") *>
            Concurrent[F].pure(Invalid(errors))
      }

  override def updateUserType(userId: String, userType: UserType): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userRepo.updateUserType(userId, userType).flatMap {
      case Valid(value) =>
        Logger[F].info(s"[UserDataService][update] Successfully updated user with ID: $userId") *>
          Concurrent[F].pure(Valid(value))
      case Invalid(errors) =>
        Logger[F].error(s"[UserDataService][update] Failed to update user with ID: $userId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }

  override def deleteUser(userId: String): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userRepo.deleteUser(userId).flatMap {
      case Valid(value) =>
        Logger[F].info(s"[UserDataService] Successfully deleted user with ID: $userId") *>
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
