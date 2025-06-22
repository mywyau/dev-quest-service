package models.quests

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.languages.*
import models.quests.CreateQuest
import models.Iron
import models.ModelsBaseSpec
import models.Open
import weaver.SimpleIOSuite

object CreateQuestSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testCreatedRequest =
    CreateQuest(
      questId = "QUEST001",
      clientId = "USER001",
      rank = Iron,
      title = "Fix this code for me",
      description = Some("Some description"),
      acceptanceCriteria = "Some acceptance criteria",
      tags = Seq(Python, Scala, TypeScript),
      status = Some(Open)
    )

  test("CreateQuest model encodes correctly to JSON") {

    val jsonResult = testCreatedRequest.asJson

    val expectedJson =
      """
        |{
        |  "questId" : "QUEST001",
        |  "clientId" : "USER001",
        |  "rank" : "Iron",
        |  "title" : "Fix this code for me",
        |  "description" : "Some description",
        |  "acceptanceCriteria" : "Some acceptance criteria",
        |  "tags" : ["Python", "Scala", "TypeScript"],          
        |  "status" : "Open"
        |}
        |""".stripMargin

    val expectedResult: Json = parse(expectedJson).getOrElse(Json.Null)

    val jsonResultPretty = printer.print(jsonResult)
    val expectedResultPretty = printer.print(expectedResult)

    val differences = jsonDiff(jsonResult, expectedResult, expectedResultPretty, jsonResultPretty)

    for {
      _ <- IO {
        if (differences.nonEmpty) {
          diffPrinter(differences, jsonResultPretty, expectedResultPretty)
        }
      }
    } yield expect(differences.isEmpty)
  }

}
