package controllers.desk

import cats.effect.*
import controllers.desk.DeskListingController
import controllers.fragments.desk.DeskPricingControllerFragments.*
import controllers.fragments.desk.DeskSpecificationsControllerFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import java.time.LocalDateTime
import java.time.LocalTime
import models.database.CreateSuccess
import models.database.UpdateSuccess
import models.desk.deskListing.DeskListing
import models.desk.deskListing.DeskListingCard
import models.desk.deskListing.InitiateDeskListingRequest
import models.desk.deskPricing.DeskPricingPartial
import models.desk.deskPricing.RetrievedDeskPricing
import models.desk.deskSpecifications.DeskSpecificationsPartial
import models.desk.deskSpecifications.PrivateDesk
import models.desk.deskSpecifications.UpdateDeskSpecificationsRequest
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.circe.jsonEncoder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import repositories.desk.DeskListingRepositoryImpl
import services.desk.DeskListingServiceImpl
import shared.HttpClientResource
import shared.TransactorResource
import testData.DeskTestConstants.*
import testData.TestConstants.*
import weaver.*

class DeskListingControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {
  type Res = (TransactorResource, HttpClientResource)
  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createDeskSpecificationsTable.update.run.transact(transactor.xa).void *>
          resetDeskSpecificationsTable.update.run.transact(transactor.xa).void *>
          insertDeskSpecifications.update.run.transact(transactor.xa).void *>
          createDeskPricingsTable.update.run.transact(transactor.xa).void *>
          resetDeskPricingsTable.update.run.transact(transactor.xa).void *>
          insertDeskPricings.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/business/listing/details/find/deskId2 - should get the desk for a given desk id"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/listing/details/find/deskId2")

    val expectedDeskSpec =
      DeskSpecificationsPartial(
        deskId = deskId2,
        deskName = "Mikey Desk 2",
        description = Some("A shared desk in a collaborative space with easy access to team members."),
        deskType = Some(PrivateDesk),
        quantity = Some(3),
        features = Some(List("Wi-Fi", "Power Outlets", "Whiteboard", "Projector")),
        openingHours = Some(deskOpeningHours),
        rules = Some("Respect others' privacy and keep noise levels to a minimum.")
      )

    val expectedDeskPricing =
      RetrievedDeskPricing(
        pricePerHour = Some(10.00),
        pricePerDay = Some(80.00),
        pricePerWeek = None,
        pricePerMonth = Some(1500.00),
        pricePerYear = Some(18000.00)
      )

    val expectedDeskListing =
      DeskListing(
        deskId = deskId2,
        specifications = expectedDeskSpec,
        pricing = expectedDeskPricing
      )

    client.run(request).use { response =>
      response.as[DeskListing].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedDeskListing
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/business/listing/details/find/deskId5 - should get the desk for a given desk id when there are nulls and missing data"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/listing/details/find/deskId5")

    val expectedDeskSpec =
      DeskSpecificationsPartial(
        deskId = "deskId5",
        deskName = "Mikey Desk 5",
        description = Some("An executive desk in a quiet, well-lit space designed for high-level work."),
        deskType = Some(PrivateDesk),
        quantity = None,
        features = None,
        openingHours = None,
        rules = None
      )

    val expectedDeskPricing =
      RetrievedDeskPricing(
        pricePerHour = None,
        pricePerDay = None,
        pricePerWeek = None,
        pricePerMonth = None,
        pricePerYear = Some(50000.00)
      )

    val expectedDeskListing =
      DeskListing(
        deskId = "deskId5",
        specifications = expectedDeskSpec,
        pricing = expectedDeskPricing
      )

    client.run(request).use { response =>
      response.as[DeskListing].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedDeskListing
        )
      }
    }
  }

  test(
    "POST - /dev-quest-service/business/desk/listing/initiate - should create/initiate a base desk listing"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val initiateRequest =
      InitiateDeskListingRequest(
        businessId = "businessId1",
        officeId = "officeId1",
        deskId = "desk006",
        deskName = "Mikey Desk 1",
        description = "A quiet, private desk perfect for focused work with a comfortable chair and good lighting."
      )

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/listing/initiate")
        .withEntity(initiateRequest.asJson)

    client.run(request).use { response =>
      response.as[CreatedResponse].map { body =>
        expect.all(
          response.status == Status.Created,
          body == CreatedResponse(CreateSuccess.toString, "Successfully created an initial desk listing")
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/business/desk/listing/cards/find/all/officeId1 - should find all the desk listings for officeId1 == 2" +
      "\nPOST - /dev-quest-service/business/desk/listing/initiate - should create an initial desk listing card" +
      "\nDELETE - /dev-quest-service/business/desk/listing/details/delete/deskId3 - should delete desk with id == deskId3"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val initiateRequest =
      InitiateDeskListingRequest(
        businessId = "businessId1",
        officeId = "officeId1",
        deskId = "desk007",
        deskName = "Mikey Desk 7",
        description = "A quiet, private desk perfect for focused work with a comfortable chair and good lighting."
      )

    val expectedDeskListing = CreatedResponse(CreateSuccess.toString, "Business Desk created successfully")

    val findAllCardsRequest =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/listing/cards/find/all/officeId1")

    val createRequest =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/listing/initiate")
        .withEntity(initiateRequest.asJson)

    val deleteRequest =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/listing/details/delete/deskId3")

    for {
      findAllResponseBefore <- client.run(findAllCardsRequest).use(_.as[List[DeskListingCard]])
      _ <- IO(expect(findAllResponseBefore.size == 2))
      createResponse <- client.run(createRequest).use(_.as[CreatedResponse])
      _ <- IO(expect(createResponse == expectedDeskListing))
      deleteResponse <- client.run(deleteRequest).use(_.as[DeletedResponse])
      _ <- IO(expect(deleteResponse.message == "Successfully deleted desk listing"))
    } yield success
  }

}
