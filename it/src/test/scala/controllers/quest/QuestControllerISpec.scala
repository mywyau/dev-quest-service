package controllers.quest

import cats.effect.*
import controller.fragments.QuestControllerFragments.*
import controllers.constants.QuestControllerConstants.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import io.circe.Json
import java.time.LocalDateTime
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.UpdatedResponse
import models.Completed
import models.InProgress
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

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
    "GET - /dev-quest-service/quest/user/USER001 - should find the quest data for given user id, returning OK and the correct quest json body"
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

    val sessionToken = "test-session-token"

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/quest/user/USER001")
        .putHeaders(Header.Raw(ci"Authorization", s"Bearer $sessionToken"))

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
    "GET - /dev-quest-service/quest/QUEST001 - should find the quest data for given quest id, returning OK and the correct quest json body"
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

  test(
    "POST - /dev-quest-service/quest/create - should generate the quest data in db table, returning Created response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    def testCreateQuest(userId: String, questId: String): CreateQuestPartial =
      CreateQuestPartial(
        userId = userId,
        questId = questId,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        status = Some(InProgress)
      )

    val businessAddressRequest: Json = testCreateQuest("user_id_6", "quest_id_6").asJson

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/quest/create")
        .withEntity(businessAddressRequest)

    val expectedBody = CreatedResponse(CreateSuccess.toString, "quest details created successfully")

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
    "PUT - /dev-quest-service/quest/QUEST004 - " +
      "should update the quest data for given quest_id, returning Updated response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val updateRequest: UpdateQuestPartial =
      UpdateQuestPartial(
        userId = "USER004",
        questId = "QUEST004",
        title = "Updated title",
        description = Some("Some updated description"),
        status = Some(Completed)
      )

    val request =
      Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/quest/update/QUEST004")
        .withEntity(updateRequest.asJson)

    val expectedBody = UpdatedResponse(UpdateSuccess.toString, "quest updated successfully")

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
    "DELETE - /dev-quest-service/quest/QUEST003 - " +
      "should delete the quest data for given quest_id, returning OK and Deleted response json"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val request =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/quest/QUEST003")

    val expectedBody = DeletedResponse(DeleteSuccess.toString, "quest deleted successfully")

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
