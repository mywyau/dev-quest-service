package controllers.user

import cats.effect.*
import controllers.fragments.UserDataControllerFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import io.circe.Json
import java.time.LocalDateTime
import models.*
import models.database.*
import models.database.CreateSuccess
import models.database.DeleteSuccess
import models.database.UpdateSuccess
import models.responses.*
import models.users.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repository.fragments.UserRepoFragments.createUserTable
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class RegistrationControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createUserDataTable.update.run.transact(transactor.xa).void *>
          resetUserDataTable.update.run.transact(transactor.xa).void *>
          insertUserData.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/registration/health -  health check should return the health response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    val reuser =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/registration/health")
      // .addCookie("auth_session", sessionToken)

    client.run(reuser).use { response =>
      response.as[GetResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == GetResponse("dev-quest-service/registration/health", "I am alive")
        )
      }
    }
  }

  // test(
  //   "GET - /dev-quest-service/registration/data/USER001 -  for given user id should find the user data, returning OK and the correct user json body"
  // ) { (transactorResource, log) =>

  //   val transactor = transactorResource._1.xa
  //   val client = transactorResource._2.client

  //   val sessionToken = "test-session-token"

  //   def testRegistration(userId: String): UserData =
  //     UserData(
  //       userId = userId,
  //       email = "bob_smith@gmail.com",
  //       firstName = Some("Bob"),
  //       lastName = Some("Smith"),
  //       userType = Some(Dev)
  //     )

  //   val reuser =
  //     Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/registration/data/USER001")
  //       .addCookie("auth_session", sessionToken)

  //   val expectedRegistration = testRegistration("USER001")

  //   client.run(reuser).use { response =>
  //     response.as[Option[UserData]].map { body =>
  //       expect.all(
  //         response.status == Status.Ok,
  //         body == Option(expectedRegistration)
  //       )
  //     }
  //   }
  // }

  test(
    "POST - /dev-quest-service/registration/data/create/USER007 - should generate the user data in db table, returning Created response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    def testCreateRegistration(): CreateUserData =
      CreateUserData(
        email = "danny_smith@gmail.com",
        firstName = Some("Danny"),
        lastName = Some("Smith"),
        userType = Some(Client)
      )

    val requestBody: Json = testCreateRegistration().asJson

    val reuser =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/registration/data/create/USER007")
        .addCookie("auth_session", sessionToken)
        .withEntity(requestBody)

    val expectedBody = CreatedResponse(CreateSuccess.toString(), "user details created successfully")

    client.run(reuser).use { response =>
      response.as[CreatedResponse].map { body =>
        expect.all(
          response.status == Status.Created,
          body == expectedBody
        )
      }
    }
  }

  // test(
  //   "PUT - /dev-quest-service/registration/data/update/type/USER008 - " +
  //     "given a valid user_id should update the user type for given user - returning Updated response"
  // ) { (transactorResource, log) =>

  //   val transactor = transactorResource._1.xa
  //   val client = transactorResource._2.client

  //   val sessionToken = "test-session-token"

  //   val updateUserTypeRequest: UpdateUserType =
  //     UpdateUserType(
  //       userId = "USER008",
  //       userType = Client
  //     )

  //   val reuser =
  //     Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/registration/data/update/type/USER008")
  //       .addCookie("auth_session", sessionToken)
  //       .withEntity(updateUserTypeRequest.asJson)

  //   val expectedBody = UpdatedResponse(UpdateSuccess.toString, "User USER008 updated successfully with type: Client")

  //   client.run(reuser).use { response =>
  //     response.as[UpdatedResponse].map { body =>
  //       expect.all(
  //         response.status == Status.Ok,
  //         body == expectedBody
  //       )
  //     }
  //   }
  // }

  // test(
  //   "DELETE - /dev-quest-service/registration/data/delete/USER009 - " +
  //     "should delete the user data for a given user_id, returning OK and Deleted response json"
  // ) { (transactorResource, log) =>

  //   val transactor = transactorResource._1.xa
  //   val client = transactorResource._2.client

  //   val sessionToken = "test-session-token"

  //   val reuser =
  //     Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/registration/data/delete/USER009")
  //       .addCookie("auth_session", sessionToken)

  //   val expectedBody = DeletedResponse(DeleteSuccess.toString, "User deleted successfully")

  //   client.run(reuser).use { response =>
  //     response.as[DeletedResponse].map { body =>
  //       expect.all(
  //         response.status == Status.Ok,
  //         body == expectedBody
  //       )
  //     }
  //   }
  // }
}
