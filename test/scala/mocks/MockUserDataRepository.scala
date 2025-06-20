package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.database.CreateSuccess
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.quests.*
import models.users.CreateUserData
import models.users.RegistrationUserDataPartial
import models.users.UpdateUserData
import models.users.UpdateUserType
import models.users.UserData
import models.QuestStatus
import repositories.UserDataRepositoryAlgebra

case object MockUserDataRepository extends UserDataRepositoryAlgebra[IO] {

  override def findUser(userId: String): IO[Option[UserData]] = ???

  override def findUserNoUserName(userId: String): IO[Option[RegistrationUserDataPartial]] = ???

  override def createUser(userId: String, createUserData: CreateUserData): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def updateUserData(userId: String, updateUserData: UpdateUserData): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def updateUserType(userId: String, userType: UpdateUserType): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def deleteUser(userId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
