package controllers

import cats.effect.*
import controllers.BaseController
import controllers.ControllerISpecBase
import doobie.util.transactor.Transactor
import models.responses.GetResponse
import org.http4s.*
import org.http4s.circe.jsonEncoder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class BaseControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)
  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test("GET - /health - should get the correct body for health") { (sharedResources, log) =>
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/health")

    client.run(request).use { response =>
      response.as[GetResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == GetResponse("success", "I am alive") // or "" if your route returns an empty string
        )
      }
    }
  }

}
