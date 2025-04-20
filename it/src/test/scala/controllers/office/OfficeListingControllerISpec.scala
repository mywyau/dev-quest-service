package controllers.office

import cats.effect.*
import controllers.constants.OfficeListingControllerConstants.*
import controllers.fragments.OfficeAddressRepoFragments.*
import controllers.fragments.OfficeContactDetailsRepoFragments.*
import controllers.fragments.OfficeSpecificationRepoFragments.*
import doobie.implicits.*
import io.circe.syntax.*
import io.circe.Json
import models.database.*
import models.office_listing.OfficeListing
import models.office_listing.OfficeListingCard
import models.responses.DeletedResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class OfficeListingControllerISpec(global: GlobalRead) extends IOSuite {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createOfficeAddressTable.update.run.transact(transactor.xa).void *>
          resetOfficeAddressTable.update.run.transact(transactor.xa).void *>
          insertOfficeAddressesTable.update.run.transact(transactor.xa).void *>

          createOfficeContactDetailsTable.update.run.transact(transactor.xa).void *>
          resetOfficeContactDetailsTable.update.run.transact(transactor.xa).void *>
          insertOfficeContactDetailsData.update.run.transact(transactor.xa).void *>

          createOfficeSpecsTable.update.run.transact(transactor.xa).void *>
          resetOfficeSpecsTable.update.run.transact(transactor.xa).void *>
          insertOfficeSpecificationsTable.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "POST - /dev-quest-service/business/office/listing/initiate - should generate the office listing data for a business in the respective tables, returning Created response, " +
      "\nGET - /dev-quest-service/business/office/listing/find/all - should retrieve all of the office listings, including the newly created listing" +
      "\nDELETE - /dev-quest-service/business/offices/listing/delete/OFF002 - given a office_id, delete the office listing data for given office id, returning OK and Deleted response json"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val businessId = "businessId1"
    val officeId = "officeId1"

    val initiateOfficeListingRequest: Json =
      testInitiateOfficeListingRequest(businessId, officeId).asJson

    // Step 1: Make the POST request to create the office listing
    val createRequest =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/office/listing/initiate")
        .withEntity(initiateOfficeListingRequest)

    client.run(createRequest).use { createResponse =>
      createResponse.as[OfficeListingCard].flatMap { createdOfficeListing =>
        expect.all(
          createResponse.status == Status.Created,
          createdOfficeListing.businessId == businessId,
          createdOfficeListing.officeId == officeId,
          createdOfficeListing.officeName == "some office name",
          createdOfficeListing.description == "some desc"
        )

        // Step 2: Make the GET request to verify all listings include the newly created one
        val findAllRequest =
          Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/office/listing/cards/find/all")

        client.run(findAllRequest).use { findAllResponse =>
          findAllResponse.as[List[OfficeListingCard]].map { allCards =>
            expect.all(
              findAllResponse.status == Status.Ok,
              allCards.size == 1
            )
          }
        }

        val request =
          Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/office/listing/delete/officeId1")

        val expectedBody = DeletedResponse(DeleteSuccess.toString, "Office listing deleted successfully")

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
  }
}
