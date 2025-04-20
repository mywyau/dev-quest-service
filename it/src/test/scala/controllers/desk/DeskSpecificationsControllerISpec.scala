package controllers.desk

import cats.effect.*
import controllers.constants.DeskSpecificationsControllerConstants.*
import controllers.desk.DeskSpecificationsController
import controllers.fragments.desk.DeskSpecificationsControllerFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import java.time.LocalDateTime
import java.time.LocalTime
import models.database.CreateSuccess
import models.database.UpdateSuccess
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
import repositories.desk.DeskSpecificationsRepositoryImpl
import services.desk.DeskSpecificationsServiceImpl
import shared.HttpClientResource
import shared.TransactorResource
import testData.DeskTestConstants.*
import testData.TestConstants.*
import utils.Diffable
import weaver.*

class DeskSpecificationsControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {
  type Res = (TransactorResource, HttpClientResource)
  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createDeskSpecificationsTable.update.run.transact(transactor.xa).void *>
          resetDeskSpecificationsTable.update.run.transact(transactor.xa).void *>
          insertDeskSpecifications.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/business/desk/specifications/details/find/deskId2 - should get the desk for a given desk id"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/specifications/details/find/deskId2")

    val expectedDeskSpecifications =
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

    client.run(request).use { response =>
      response.as[DeskSpecificationsPartial].map { body =>
        Diffable.logDifferences(expectedDeskSpecifications, body)
        expect.all(
          response.status == Status.Ok,
          body == expectedDeskSpecifications
        )
      }
    }
  }

  test(
    "PUT - /dev-quest-service/business/desk/specifications/details/update/deskId1 - should update the desk for a given desk id"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val updateRequest =
      UpdateDeskSpecificationsRequest(
        deskName = "Updated desk 1",
        description = Some("Updated desk specifications"),
        deskType = PrivateDesk,
        quantity = 5,
        features = List("Wi-Fi", "Power Outlets", "Ergonomic Chair", "Desk Lamp"),
        openingHours = deskOpeningHours,
        rules = Some("No loud conversations, please keep the workspace clean.")
      )

    val request =
      Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/specifications/details/update/deskId1")
        .withEntity(updateRequest.asJson)

    client.run(request).use { response =>
      response.as[UpdatedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == UpdatedResponse(UpdateSuccess.toString, "desk specifications updated successfully")
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/business/desk/specifications/details/find/all/officeId1 - should find all the desk specifications for office01" +
      "\nPOST - /dev-quest-service/business/desk/specifications/create - should create a desk specifications" +
      "\nDELETE - /dev-quest-service/business/desk/specifications/details/delete/deskId3 - should delete a desk specifications for a given deskId"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val findAllRequest =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/specifications/details/find/all/officeId1")

    val createRequest =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/specifications/details/create")
        .withEntity(testDeskSpecificationsRequest.asJson)

    val deleteRequest =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/desk/specifications/details/delete/deskId3")

    val expectedDeskSpecifications = CreatedResponse(CreateSuccess.toString, "Business Desk created successfully")

    for {
      findAllResponseBefore <- client.run(findAllRequest).use(_.as[List[DeskSpecificationsPartial]])
      _ <- IO(expect(findAllResponseBefore.size == 5))
      createResponse <- client.run(createRequest).use(_.as[CreatedResponse])
      deleteResponse <- client.run(deleteRequest).use(_.as[DeletedResponse])
      _ <- IO(expect(deleteResponse.message == "Successfully deleted desk specifications"))
      findAllResponseAfter <- client.run(findAllRequest).use(_.as[List[DeskSpecificationsPartial]])
      _ <- IO(expect(findAllResponseAfter.isEmpty))
    } yield success

  }
}
