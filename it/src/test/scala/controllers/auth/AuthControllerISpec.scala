package controllers.auth

import cats.effect.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.Json
import io.circe.syntax.*
import models.Completed
import models.InProgress
import models.database.*
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.GetResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shared.HttpClientResource
import shared.RedisCacheResource
import shared.TransactorResource
import weaver.*

import java.time.LocalDateTime

class AuthControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (RedisCacheResource, HttpClientResource)

  val sessionToken = "test-session-token"

  def sharedResource: Resource[IO, Res] =
    for {
      cache <- global.getOrFailR[RedisCacheResource]()
      _ <-
        Resource.eval(
          cache._1.storeSession("USER001", sessionToken).void *>
            cache._1.storeSession("USER002", sessionToken).void *>
            cache._1.storeSession("USER003", sessionToken)
        )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (cache, client)

  test(
    "GET - /auth/session/USER001 - should find the cookie session data for given user id, returning OK and the correct auth json body"
  ) { (cacheResource, log) =>

    val cache = cacheResource._1.redis
    val client = cacheResource._2.client

    val sessionToken = "test-session-token"

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/auth/session/USER001")
        .putHeaders(Header.Raw(ci"Authorization", s"Bearer $sessionToken"))

    client.run(request).use { response =>
      response.as[GetResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == GetResponse("200", s"Session token: $sessionToken")
        )
      }
    }
  }

  // test(
  //   "POST - /dev-auth-service/auth/create/USER006 - should generate the auth data in db table, returning Created response"
  // ) { (cacheResource, log) =>

  //   val transactor = cacheResource._1.xa
  //   val client = cacheResource._2.client

  //   val sessionToken = "test-session-token"

  //   def testCreateAuth(userId: String, authId: String): CreateAuthPartial =
  //     CreateAuthPartial(
  //       userId = userId,
  //       authId = authId,
  //       title = "Implement User Authentication",
  //       description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
  //       status = Some(InProgress)
  //     )

  //   val businessAddressRequest: Json = testCreateAuth("user_id_6", "auth_id_6").asJson

  //   val request =
  //     Request[IO](POST, uri"http://127.0.0.1:9999/dev-auth-service/auth/create/USER006")
  //       .putHeaders(Header.Raw(ci"Authorization", s"Bearer $sessionToken"))
  //       .withEntity(businessAddressRequest)

  //   val expectedBody = CreatedResponse(CreateSuccess.toString, "auth details created successfully")

  //   client.run(request).use { response =>
  //     response.as[CreatedResponse].map { body =>
  //       expect.all(
  //         response.status == Status.Created,
  //         body == expectedBody
  //       )
  //     }
  //   }
  // }

  // test(
  //   "PUT - /dev-auth-service/auth/USER004/QUEST004 - " +
  //     "should update the auth data for given auth_id, returning Updated response"
  // ) { (cacheResource, log) =>

  //   val transactor = cacheResource._1.xa
  //   val client = cacheResource._2.client

  //   val sessionToken = "test-session-token"

  //   val updateRequest: UpdateAuthPartial =
  //     UpdateAuthPartial(
  //       userId = "USER004",
  //       authId = "QUEST004",
  //       title = "Updated title",
  //       description = Some("Some updated description"),
  //       status = Some(Completed)
  //     )

  //   val request =
  //     Request[IO](PUT, uri"http://127.0.0.1:9999/dev-auth-service/auth/update/USER004/QUEST004")
  //       .putHeaders(Header.Raw(ci"Authorization", s"Bearer $sessionToken"))
  //       .withEntity(updateRequest.asJson)

  //   val expectedBody = UpdatedResponse(UpdateSuccess.toString, "auth updated successfully")

  //   client.run(request).use { response =>
  //     response.as[UpdatedResponse].map { body =>
  //       expect.all(
  //         response.status == Status.Ok,
  //         body == expectedBody
  //       )
  //     }
  //   }
  // }

  // test(
  //   "DELETE - /dev-auth-service/auth/USER003/QUEST003 - " +
  //     "should delete the auth data for given auth_id, returning OK and Deleted response json"
  // ) { (cacheResource, log) =>

  //   val transactor = cacheResource._1.xa
  //   val client = cacheResource._2.client

  //   val sessionToken = "test-session-token"

  //   val request =
  //     Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-auth-service/auth/USER003/QUEST003")
  //       .putHeaders(Header.Raw(ci"Authorization", s"Bearer $sessionToken"))

  //   val expectedBody = DeletedResponse(DeleteSuccess.toString, "auth deleted successfully")

  //   client.run(request).use { response =>
  //     response.as[DeletedResponse].map { body =>
  //       expect.all(
  //         response.status == Status.Ok,
  //         body == expectedBody
  //       )
  //     }
  //   }
  // }
}
