package repository

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import java.time.LocalDateTime
import models.database.*
import models.database.DeleteSuccess
import models.database.UpdateSuccess
import models.users.CreateUserData
import models.users.Registration
import models.users.UserData
import models.Client
import models.Completed
import models.Dev
import models.InProgress
import repositories.UserDataRepositoryImpl
import repository.fragments.UserRepoFragments.*
import repository.RepositoryISpecBase
import shared.TransactorResource
import testData.TestConstants.*
import weaver.GlobalRead
import weaver.IOSuite
import weaver.ResourceTag

class UserDataRepositoryISpec(global: GlobalRead) extends IOSuite with RepositoryISpecBase {
  type Res = UserDataRepositoryImpl[IO]

  private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
    Resource.eval(
      createUserTable.update.run.transact(transactor.xa).void *>
        resetUserTable.update.run.transact(transactor.xa).void *>
        insertUserData.update.run.transact(transactor.xa).void
    )

  def sharedResource: Resource[IO, UserDataRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      userRepo = new UserDataRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield userRepo

    setup
  }

  test(".findUser() - should find and return the user type if user_id exists for a previously created user") { userRepo =>

    val expectedResult =
      UserData(
        userId = "USER001",
        email = "bob_smith@gmail.com",
        username = "cloud",
        firstName = Some("Bob"),
        lastName = Some("Smith"),
        userType = Some(Dev)
      )

    for {
      users <- userRepo.findUser("USER001")
    } yield expect(users == Option(expectedResult))
  }

  test(".registerUser() - should find and update the user's type if user_id exists for a previously created user returning UpdateSuccess response") { userRepo =>

    val orignalUser =
      UserData(
        userId = "USER002",
        email = "dylan_smith@gmail.com",
        username = "",
        firstName = None,
        lastName = None,
        userType = Some(Dev)
      )

    val expectedResult =
      UserData(
        userId = "USER002",
        email = "dylan_smith@gmail.com",
        username = "tifa2",
        firstName = Some("Dylan"),
        lastName = Some("Smith"),
        userType = Some(Dev)
      )

    for {
      originalData <- userRepo.findUser("USER002")
      result <- userRepo.registerUser(
        "USER002",
        Registration(
          username = "tifa2",
          firstName = "Dylan",
          lastName = "Smith",
          userType = Dev
        )
      )
      updatedUser <- userRepo.findUser("USER002")
    } yield expect.all(
      originalData == Some(orignalUser),
      result == Valid(UpdateSuccess),
      updatedUser == Some(expectedResult)
    )
  }

  test(".deleteUser() - should delete the USER003 user if user_id exists for the previously existing user") { userRepo =>

    val userId = "USER003"

    val expectedResult =
      UserData(
        userId = "USER003",
        email = "sam_smith@gmail.com",
        username = "tidus",
        firstName = Some("Sam"),
        lastName = Some("Smith"),
        userType = Some(Dev)
      )

    for {
      firstFindResult <- userRepo.findUser(userId)
      deleteResult <- userRepo.deleteUser(userId)
      afterDeletionFindResult <- userRepo.findUser(userId)
    } yield expect.all(
      firstFindResult == Some(expectedResult),
      deleteResult == Valid(DeleteSuccess),
      afterDeletionFindResult == None
    )
  }
}
