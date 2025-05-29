package controllers.user

import cats.effect.*
import controllers.ControllerISpecBase
import controllers.fragments.UserDataControllerFragments.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.Json
import io.circe.syntax.*
import models.*
import models.database.*
import models.database.CreateSuccess
import models.database.DeleteSuccess
import models.database.UpdateSuccess
import models.responses.*
import models.users.*
import org.http4s.*
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

import java.time.LocalDateTime

class UserDataControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

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
    "GET - /dev-quest-service/user/data/USER001 -  for given user id should find the user data, returning OK and the correct user json body"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    def testUserData(userId: String): UserData =
      UserData(
        userId = userId,
        email = "bob_smith@gmail.com",
        firstName = Some("Bob"),
        lastName = Some("Smith"),
        userType = Some(Dev)
      )

    val reuser =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/user/data/USER001")
        .addCookie("auth_session", sessionToken)

    val expectedUserData = testUserData("USER001")

    client.run(reuser).use { response =>
      response.as[Option[UserData]].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == Option(expectedUserData)
        )
      }
    }
  }

  test(
    "POST - /dev-quest-service/user/data/create/USER006 - should generate the user data in db table, returning Created response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    def testCreateUserData(userId: String): CreateUserData =
      CreateUserData(
        email = "sally_smith@gmail.com",
        firstName = Some("Sally"),
        lastName = Some("Smith"),
        userType = Some(Client)
      )

    val requestBody: Json = testCreateUserData("USER006").asJson

    val reuser =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/user/data/create/USER006")
        .addCookie("auth_session", sessionToken)
        .withEntity(requestBody)

    val expectedBody = CreatedResponse(CreateSuccess.toString, "user details created successfully")

    client.run(reuser).use { response =>
      response.as[CreatedResponse].map { body =>
        expect.all(
          response.status == Status.Created,
          body == expectedBody
        )
      }
    }
  }

  test(
    "PUT - /dev-quest-service/user/data/update/USER008 - " +
      "given a valid user_id should update the user type for given user - returning Updated response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    val updateUserDataRequest: UpdateUserData =
      UpdateUserData(
        firstName = Some("Popo"),
        lastName = Some("Smith"),
        email = "updateEmail@gmail.com",
        userType = Some(Client)
      )

    val reuser =
      Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/user/data/update/USER008")
        .addCookie("auth_session", sessionToken)
        .withEntity(updateUserDataRequest.asJson)

    val expectedBody = UpdatedResponse(UpdateSuccess.toString, "User USER008 updated successfully")

    client.run(reuser).use { response =>
      response.as[UpdatedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBody
        )
      }
    }
  }

  test(
    "PUT - /dev-quest-service/user/update/type/USER003 - " +
      "given a valid user_id should update the user type for given user - returning Updated response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    val updateUserTypeRequest: UpdateUserType =
      UpdateUserType(
        userType = Client
      )

    val reuser =
      Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/user/update/type/USER003")
        .addCookie("auth_session", sessionToken)
        .withEntity(updateUserTypeRequest.asJson)

    val expectedBody = UpdatedResponse(UpdateSuccess.toString, "User USER003 updated successfully with type: Client")

    client.run(reuser).use { response =>
      response.as[UpdatedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBody
        )
      }
    }
  }

  test(
    "DELETE - /dev-quest-service/user/data/delete/USER004 - " +
      "should delete the user data for a given user_id, returning OK and Deleted response json"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    val reuser =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/user/data/delete/USER004")
        .addCookie("auth_session", sessionToken)

    val expectedBody = DeletedResponse(DeleteSuccess.toString, "User deleted successfully")

    client.run(reuser).use { response =>
      response.as[DeletedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBody
        )
      }
    }
  }
}
