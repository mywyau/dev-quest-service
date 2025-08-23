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
import models.NoUserType
import models.UserType
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.users.CreateUserData
import models.users.Registration
import models.users.UserData
import org.typelevel.log4cats.Logger
import repositories.PricingPlanRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra
import repositories.UserDataRepositoryImpl
import repositories.UserPricingPlanRepositoryAlgebra

import java.util.UUID

trait RegistrationServiceAlgebra[F[_]] {

  def getUser(userId: String): F[Option[UserData]]

  def createUser(userId: String, createRegistration: CreateUserData): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]

  def registerUser(userId: String, userType: Registration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]]
}

class RegistrationServiceImpl[F[_] : Concurrent : Monad : Logger](
  userDataRepo: UserDataRepositoryAlgebra[F],
  userPricingPlanService: UserPricingPlanServiceAlgebra[F]
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
        case Valid(createdUser) =>
          Logger[F].debug(s"[RegistrationService][createUser] Successfully created initial user profile data for $userId") *>
            Valid(createdUser).pure[F]
        case Invalid(errors) =>
          Logger[F].error(s"[RegistrationService][createUser] Failed to create user: ${errors.toList.mkString(", ")}") *>
            Invalid(errors).pure[F]
      }

    Logger[F].debug(s"[RegistrationService][createUser] Creating user $userId") *>
      userDataRepo.findUserNoUserName(userId).flatMap {
        case None => createUserWithLogging
        case Some(value) =>
          Logger[F].debug(s"[RegistrationService][createUser] User already exists ${value.userId}") *>
            Valid(ReadSuccess(value)).pure[F]
      }
  }

  override def registerUser(userId: String, registration: Registration): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userDataRepo.registerUser(userId, registration).flatMap {
      case Valid(databaseSuccess) =>
        Logger[F].debug(s"[RegistrationService][registerUser] Successfully updated user with ID: $userId") *>
          userPricingPlanService.ensureDefaultPlan(userId, userType = registration.userType)
          .attempt
          .flatMap {
            case Right(_) => 
              Valid(databaseSuccess).pure[F]
            case Left(e) =>
              // Don’t fail user creation because plan seeding failed; just log.
              Logger[F].error(e)(s"[RegistrationService][registerUser] Failed default plan for $userId") *>
                Valid(databaseSuccess).pure[F]
          }
      case Invalid(errors) =>
        Logger[F].error(s"[RegistrationService][registerUser] Failed to update user with ID: $userId. Errors: ${errors.toList.mkString(", ")}") *>
          Concurrent[F].pure(Invalid(errors))
    }

}

object RegistrationService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    userDataRepo: UserDataRepositoryAlgebra[F],
    userPricingPlanService: UserPricingPlanServiceAlgebra[F]
  ): RegistrationServiceAlgebra[F] =
    new RegistrationServiceImpl[F](userDataRepo, userPricingPlanService)
}
