package controllers.office

import cats.effect.*
import configuration.models.AppConfig
import controllers.constants.OfficeAddressControllerConstants.*
import controllers.fragments.OfficeAddressRepoFragments.createOfficeAddressTable
import controllers.fragments.OfficeAddressRepoFragments.insertOfficeAddressesTable
import controllers.fragments.OfficeAddressRepoFragments.resetOfficeAddressTable
import controllers.office.OfficeAddressController
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import io.circe.Json
import java.time.LocalDateTime
import models.database.*
import models.office.address_details.CreateOfficeAddressRequest
import models.office.address_details.OfficeAddress
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.Server
import org.http4s.Method.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repositories.office.OfficeAddressRepository
import services.office.OfficeAddressService
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class OfficeAddressControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createOfficeAddressTable.update.run.transact(transactor.xa).void *>
          resetOfficeAddressTable.update.run.transact(transactor.xa).void *>
          insertOfficeAddressesTable.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/business/offices/address/details/OFF001 - " +
      "given a office_id, find the office address data for given id, returning OK and the address json"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/offices/address/details/OFF001")

    val expectedOfficeAddress = testCreateOfficeAddressRequest("BUS123", "OFF001")

    client.run(request).use { response =>
      response.as[CreateOfficeAddressRequest].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedOfficeAddress
        )
      }
    }
  }

  test(
    "POST - /dev-quest-service/business/offices/address/details/create - " +
      "should generate the office address data in postgresql, returning Created response"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val businessListingRequest: Json = testCreateOfficeAddressRequest("BUSINESS1337", "OFFICE1337").asJson

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/offices/address/details/create")
        .withEntity(businessListingRequest)

    val expectedBody = CreatedResponse(CreateSuccess.toString, "Office address created successfully")

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
    "DELETE - /dev-quest-service/business/offices/address/details/OFF002 - " +
      "given a office_id, delete the office address data for given office id, returning OK and Deleted response json"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/offices/address/details/OFF002")

    val expectedBody = DeletedResponse(DeleteSuccess.toString, "Office address deleted successfully")

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
