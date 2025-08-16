package controllers.quest

import cats.effect.*
import cats.effect.IO
import cats.implicits.*
import controllers.ControllerISpecBase
import controllers.fragments.QuestControllerFragments.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import fs2.Stream
import fs2.text.lines
import fs2.text.utf8Decode
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import models.Completed
import models.Demonic
import models.InProgress
import models.NotStarted
import models.auth.UserSession
import models.database.*
import models.languages.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.UpdatedResponse
import org.http4s.*
import org.http4s.Method.*
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shared.HttpClientResource
import shared.TransactorResource
import testData.ITestConstants.*
import weaver.*

import java.time.LocalDateTime
import scala.collection.immutable.ArraySeq

class QuestControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createQuestTable.update.run.transact(transactor.xa).void *>
          resetQuestTable.update.run.transact(transactor.xa).void *>
          insertQuestData.update.run.transact(transactor.xa).void *>
          insertQuestDataNoDevId.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  val sessionToken = "test-session-token"

  def fakeUserSession(clientId: String) =
    UserSession(
      userId = "USER001",
      cookieValue = sessionToken,
      email = "fakeEmail@gmail.com",
      userType = "Dev"
    )

  test(
    "GET - /dev-quest-service/quest/USER001/QUEST001 - should find the quest data for given quest id, returning OK and the correct quest json body"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    def testQuest(clientId: String, questId: String, devId:Option[String]): QuestPartial =
      QuestPartial(
        clientId = clientId,
        devId = devId,
        questId = questId,
        rank = Demonic,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(InProgress),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    val sessionToken = "test-session-token"

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/quest/USER001/QUEST001")
        .addCookie("auth_session", sessionToken)

    val expectedQuest = testQuest("USER001", "QUEST001", Some("DEV001"))

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
    "GET - /dev-quest-service/quest/USER001/QUEST016 - should find the quest data for given quest id, returning OK and the correct quest json body"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    def testQuest(clientId: String, questId: String): QuestPartial =
      QuestPartial(
        questId = questId,
        clientId = clientId,
        devId = None,
        rank = Demonic,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(InProgress),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    val sessionToken = "test-session-token"

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/quest/USER001/QUEST016")
        .addCookie("auth_session", sessionToken)

    val expectedQuest = testQuest("USER001", "QUEST016")

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
    "GET /dev-quest-service/quest/stream/client/new/USER007?status=InProgress&page=1&limit=10 - streams the right quest"
  ) { (transactorResource, log) =>
    val xa = transactorResource._1.xa
    val client = transactorResource._2.client

    def testQuest(id:Int, clientId: String, questId: String, devId:Option[String]) =
      QuestPartial(
        clientId = clientId,
        questId = questId,
        devId = devId,
        rank = Demonic,
        title = s"Some Quest Title $id",
        description = Some(s"Some Quest Description $id"),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(InProgress),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    val expected: List[QuestPartial] = List(
      testQuest(1, "USER007", "QUEST010", Some("DEV006")),
      testQuest(2, "USER007", "QUEST011", Some("DEV007"))
    )

    val req = Request[IO](
      GET,
      uri"http://127.0.0.1:9999/dev-quest-service/quest/stream/client/new/USER007?status=InProgress&page=1&limit=10"
    ).addCookie("auth_session", "test-session-token")

    client.run(req).use { resp =>
      for {
        bodyLines: List[String] <- resp.body
          .through(utf8Decode)
          .through(lines)
          .filter(_.nonEmpty) // drop any blank trailing newline
          .compile
          .toList
        // 3) parse each line as JSON → QuestPartial
        parsed: List[QuestPartial] <- bodyLines.traverse { line =>
          IO.fromEither(decode[QuestPartial](line).left.map(err => new Exception(s"Failed to decode line [$line]: $err")))
        }
      } yield (
        expect.all(
          resp.status == Status.Ok,
          parsed == expected,
        )
      )
    }
  }

  test(
    "GET /dev-quest-service/quest/stream/client/new/USER007?status=NotStarted&page=1&limit=10 - streams the right quest"
  ) { (transactorResource, log) =>
    val xa = transactorResource._1.xa
    val client = transactorResource._2.client

    def testQuest(id:Int, clientId: String, questId: String, devId:Option[String]) =
      QuestPartial(
        clientId = clientId,
        devId = devId,
        questId = questId,
        rank = Demonic,
        title = s"Some Quest Title $id",
        description = Some(s"Some Quest Description $id"),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(NotStarted),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    val expected: List[QuestPartial] = List(
      testQuest(5, "USER007", "QUEST014", Some("DEV010")),
      testQuest(6, "USER007", "QUEST015", Some("DEV011"))
    )

    val req = Request[IO](
      GET,
      uri"http://127.0.0.1:9999/dev-quest-service/quest/stream/client/new/USER007?status=NotStarted&page=1&limit=10"
    ).addCookie("auth_session", "test-session-token")

    client.run(req).use { resp =>
      for {
        bodyLines: List[String] <- resp.body
          .through(utf8Decode)
          .through(lines)
          .filter(_.nonEmpty) // drop any blank trailing newline
          .compile
          .toList
        // 3) parse each line as JSON → QuestPartial
        parsed: List[QuestPartial] <- bodyLines.traverse { line =>
          IO.fromEither(decode[QuestPartial](line).left.map(err => new Exception(s"Failed to decode line [$line]: $err")))
        }
      } yield (
        expect.all(
          resp.status == Status.Ok,
          parsed == expected,
        )
      )
    }
  }


  test(
    "POST - /dev-quest-service/quest/create/USER006 - should generate the quest data in db table, returning Created response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    def testCreateQuest(): CreateQuestPartial =
      CreateQuestPartial(
        rank = Demonic,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        acceptanceCriteria = "Some acceptance criteria",
        tags = Seq(Python, Scala, TypeScript)
      )

    val createRequest: Json = testCreateQuest().asJson

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/quest/create/USER006")
        .addCookie("auth_session", sessionToken)
        .withEntity(createRequest)

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
    "PUT - /dev-quest-service/quest/update/details/USER004/QUEST004 - " +
      "should update the quest data for given quest_id, returning Updated response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    val updateRequest: UpdateQuestPartial =
      UpdateQuestPartial(
        rank = Demonic,
        title = "Updated title",
        description = Some("Some updated description"),
        acceptanceCriteria = Some("Some updated description")
      )

    val request =
      Request[IO](PUT, uri"http://127.0.0.1:9999/dev-quest-service/quest/update/details/USER004/QUEST004")
        .addCookie("auth_session", sessionToken)
        .withEntity(updateRequest.asJson)

    val expectedBody = UpdatedResponse(UpdateSuccess.toString, "Quest QUEST004 updated successfully")

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
    "DELETE - /dev-quest-service/quest/USER003/QUEST003 - " +
      "should delete the quest data for given quest_id, returning OK and Deleted response json"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"

    val request =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/quest/USER003/QUEST003")
        .addCookie("auth_session", sessionToken)

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
