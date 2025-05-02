package controllers.quest

import cats.effect.*
import controller.fragments.QuestControllerFragments.*
import controllers.ControllerISpecBase
import controllers.constants.QuestControllerConstants.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.Json
import io.circe.syntax.*
import models.InProgress
import models.database.*
import models.quests.QuestPartial
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

import java.time.LocalDateTime

class QuestControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createQuestTable.update.run.transact(transactor.xa).void *>
          resetQuestTable.update.run.transact(transactor.xa).void *>
          insertQuestData.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/quest/user/USER001 - " +
      "given a user_id, find the quest data for given user id, returning OK and the correct quest json body"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    def testQuest(userId: String, questId: String): QuestPartial =
      QuestPartial(
        userId = userId,
        questId = questId,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        status = Some(InProgress)
      )

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/quest/user/USER001")

    val expectedQuest = testQuest("USER001", "QUEST001")

    client.run(request).use { response =>
      response.as[QuestPartial].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedQuest
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/quest/QUEST001 - " +
      "given a quest_id, find the quest data for given quest id, returning OK and the correct quest json body"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    def testQuest(userId: String, questId: String): QuestPartial =
      QuestPartial(
        userId = userId,
        questId = questId,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        status = Some(InProgress)
      )

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/quest/QUEST001")

    val expectedQuest = testQuest("USER001", "QUEST001")

    client.run(request).use { response =>
      response.as[QuestPartial].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedQuest
        )
      }
    }
  }

//   test(
//     "POST - /dev-quest-service/business/businesses/address/details/create - " +
//       "should generate the business address data for a business in database table, returning Created response"
//   ) { (transactorResource, log) =>

//     val transactor = transactorResource._1.xa
//     val client = transactorResource._2.client

//     val businessAddressRequest: Json = testQuestRequest("user_id_3", "business_id_3").asJson

//     val request =
//       Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/address/details/create")
//         .withEntity(businessAddressRequest)

//     val expectedBody = CreatedResponse(CreateSuccess.toString, "Business address details created successfully")

//     client.run(request).use { response =>
//       response.as[CreatedResponse].map { body =>
//         expect.all(
//           response.status == Status.Created,
//           body == expectedBody
//         )
//       }
//     }
//   }

//   test(
//     "PUT - /dev-quest-service/business/businesses/address/details/update/business_id_4 - " +
//       "should update the business address data for a business in database table, returning Updated response"
//   ) { (transactorResource, log) =>

//     val transactor = transactorResource._1.xa
//     val client = transactorResource._2.client

//     val updateRequest: UpdateQuestRequest =
//       UpdateQuestRequest(
//         buildingName = Some("Mikey Building"),
//         floorNumber = Some("Mikey Floor"),
//         street = "Mikey Street",
//         city = "Mikey City",
//         country = "Mikey Country",
//         county = "Mikey County",
//         postcode = "CF3 3NJ",
//         latitude = 100.1,
//         longitude = -100.1
//       )

//     val request =
//       Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/address/details/update/business_id_4")
//         .withEntity(updateRequest.asJson)

//     val expectedBody = UpdatedResponse(UpdateSuccess.toString, "Business address updated successfully")

//     client.run(request).use { response =>
//       response.as[UpdatedResponse].map { body =>
//         expect.all(
//           response.status == Status.Ok,
//           body == expectedBody
//         )
//       }
//     }
//   }

//   test(
//     "DELETE - /dev-quest-service/business/businesses/address/details/business_id_2 - " +
//       "given a business_id, delete the business address details data for given business id, returning OK and Deleted response json"
//   ) { (transactorResource, log) =>

//     val transactor = transactorResource._1.xa
//     val client = transactorResource._2.client

//     val request =
//       Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/address/details/business_id_2")

//     val expectedBody = DeletedResponse(DeleteSuccess.toString, "Business address details deleted successfully")

//     client.run(request).use { response =>
//       response.as[DeletedResponse].map { body =>
//         expect.all(
//           response.status == Status.Ok,
//           body == expectedBody
//         )
//       }
//     }
//   }
}
