package controllers.desk

import cats.effect.*
import controllers.constants.DeskPricingControllerConstants.testUpdateRequest
import controllers.desk.DeskPricingController
import controllers.fragments.desk.DeskPricingControllerFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import java.time.LocalDateTime
import java.time.LocalTime
import models.database.CreateSuccess
import models.database.UpdateSuccess
import models.desk.deskPricing.RetrievedDeskPricing
import models.desk.deskPricing.UpdateDeskPricingRequest
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.circe.jsonEncoder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import repositories.desk.DeskPricingRepositoryImpl
import services.desk.DeskPricingServiceImpl
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class DeskPricingControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {
  type Res = (TransactorResource, HttpClientResource)
  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createDeskPricingsTable.update.run.transact(transactor.xa).void *>
          resetDeskPricingsTable.update.run.transact(transactor.xa).void *>
          insertDeskPricings.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/desk/pricing/find/deskId2 - should get the desk for a given desk id"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/desk/pricing/find/deskId2")

    val expectedDeskPricing =
      RetrievedDeskPricing(
        pricePerHour = Some(10.0),
        pricePerDay = Some(80.0),
        pricePerWeek = None,
        pricePerMonth = Some(1500.0),
        pricePerYear = Some(18000.0)
      )

    client.run(request).use { response =>
      response.as[RetrievedDeskPricing].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedDeskPricing
        )
      }
    }
  }

  test(
    "PUT - /dev-quest-service/desk/pricing/update/deskId1 - should update the desk for a given desk id"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val updateRequest =
      UpdateDeskPricingRequest(
        pricePerHour = 999.0,
        pricePerDay = Some(999.0),
        pricePerWeek = Some(999.0),
        pricePerMonth = Some(9999.0),
        pricePerYear = Some(99999.0)
      )

    val request =
      Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/desk/pricing/update/deskId1")
        .withEntity(updateRequest.asJson)

    client.run(request).use { response =>
      response.as[UpdatedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == UpdatedResponse(UpdateSuccess.toString, "desk pricings updated successfully")
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/desk/pricing/find/all/office01 - should find all the desk pricings for office01" +
      "\nPOST - /dev-quest-service/desk/pricing/create - should create a desk pricing" +
      "\nDELETE - /dev-quest-service/desk/pricing/delete/deskId3 - should delete a desk pricing for a given deskId"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val findAllRequest =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/desk/pricing/find/all/office01")

    // val createRequest =
    //   Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/desk/pricing/create")
    //     .withEntity(testDeskPricingRequest.asJson)

    val deleteRequest =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/desk/pricing/delete/deskId3")

    val expectedDeskPricing = CreatedResponse(CreateSuccess.toString, "Business Desk created successfully")

    for {
      findAllResponseBefore <- client.run(findAllRequest).use(_.as[List[RetrievedDeskPricing]])
      _ <- IO(expect(findAllResponseBefore.size == 5))
      deleteResponse <- client.run(deleteRequest).use(_.as[DeletedResponse])
      _ <- IO(expect(deleteResponse.message == "Successfully deleted desk pricing"))
      findAllResponseAfter <- client.run(findAllRequest).use(_.as[List[RetrievedDeskPricing]])
      _ <- IO(expect(findAllResponseAfter.isEmpty))
    } yield success

  }
}
