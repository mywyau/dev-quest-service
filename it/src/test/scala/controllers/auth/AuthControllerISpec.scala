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
            cache._1.storeSession("USER003", sessionToken).void *>
            cache._1.storeSession("USER004", sessionToken)
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

    client.run(request).use { response =>
      response.as[GetResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == GetResponse("200", s"Session token: $sessionToken")
        )
      }
    }
  }

  test(
    "POST - /auth/create/USER006 - should generate the auth data in db table, returning Created response"
  ) { (cacheResource, log) =>

    val cache = cacheResource._1.redis
    val client = cacheResource._2.client

    val sessionToken = "test-session-token"

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/auth/session/USER006")
        .addCookie("auth_session", sessionToken)

    val expectedBody = CreatedResponse("USER006", "Session stored from cookie")

    client.run(request).use { response =>
      response.as[CreatedResponse].map { body =>
        expect.all(
          response.status == Status.Created,
          body == expectedBody
        )
      }
    }
  }

  test(
    "PUT - /auth/session/USER004 - " +
      "should update the auth data for given user_id, returning Updated response"
  ) { (cacheResource, log) =>

    val cache = cacheResource._1.redis
    val client = cacheResource._2.client

    val sessionToken = "updated-test-session-token"

    val request =
      Request[IO](PUT, uri"http://127.0.0.1:9999/auth/session/USER004")
        .addCookie("auth_session", sessionToken)

    val expectedBody = UpdatedResponse("USER004", "Session updated")

    client.run(request).use { response =>
      response.as[UpdatedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBody
        )
      }
    }
  }

  test(
    "DELETE - /auth/session/USER003 - " +
      "should delete the auth data for given user_id, returning OK and Deleted response json"
  ) { (cacheResource, log) =>

    val cache = cacheResource._1.redis
    val client = cacheResource._2.client

    val request =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/auth/session/USER003")

    val expectedBody = DeletedResponse("USER003", "Session deleted")

    client.run(request).use { response =>
      response.as[DeletedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBody
        )
      }
    }
  }
}
