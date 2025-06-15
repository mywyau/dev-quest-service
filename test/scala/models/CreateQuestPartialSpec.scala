package models

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.ModelsBaseSpec
import models.quests.CreateQuestPartial
import weaver.SimpleIOSuite

import java.time.LocalDateTime

object CreateQuestPartialSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testCreatedRequest =
    CreateQuestPartial(
      rank = Iron,
      title = "Some quest title",
      description = Some("Some description"),
      acceptanceCriteria = "Some acceptance criteria"
    )

  test("CreateQuestPartial model encodes correctly to JSON") {

    val jsonResult = testCreatedRequest.asJson

    val expectedJson =
      """
        |{
        |  "acceptanceCriteria" : "Some acceptance criteria",
        |  "description" : "Some description",
        |  "rank" : "Iron",
        |  "title" : "Some quest title"          
        |}
        |""".stripMargin

    val expectedResult: Json = parse(expectedJson).getOrElse(Json.Null)

    val jsonResultPretty = printer.print(jsonResult)
    val expectedResultPretty = printer.print(expectedResult)

    val differences = jsonDiff(jsonResult, expectedResult, expectedResultPretty, jsonResultPretty)

    for {
      _ <- IO {
        if (differences.nonEmpty) {
          println("=== JSON Difference Detected! ===")
          differences.foreach(diff => println(s"- $diff"))
          println("Generated JSON:\n" + jsonResultPretty)
          println("Expected JSON:\n" + expectedResultPretty)
        }
      }
    } yield expect(differences.isEmpty)
  }

}