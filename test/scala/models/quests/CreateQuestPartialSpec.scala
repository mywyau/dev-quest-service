package models.quests

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.languages.*
import models.quests.CreateQuestPartial
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object CreateQuestPartialSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testCreatedRequest =
    CreateQuestPartial(
      rank = Iron,
      title = "Some quest title",
      description = Some("Some description"),
      acceptanceCriteria = "Some acceptance criteria",
      tags = Seq(Python, Scala, TypeScript)
    )

  test("CreateQuestPartial model encodes correctly to JSON") {

    val jsonResult = testCreatedRequest.asJson

    val expectedJson =
      """
        |{
        |  "acceptanceCriteria" : "Some acceptance criteria",
        |  "description" : "Some description",
        |  "rank" : "Iron",
        |  "title" : "Some quest title",         
        |  "tags" : ["Python", "Scala", "TypeScript"]          
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
