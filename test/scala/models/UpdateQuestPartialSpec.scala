package models

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.quests.UpdateQuestPartial
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object UpdateQuestPartialSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testUpdatedRequest =
    UpdateQuestPartial(
      rank = Iron,
      title = "Some quest title",
      description = Some("Some description"),
      acceptanceCriteria = Some("Some acceptance criteria")
    )

  test("UpdateQuestPartial model encodes correctly to JSON") {

    val jsonResult = testUpdatedRequest.asJson

    val expectedJson =
      """
        |{
        |  "rank": "Iron",
        |  "title": "Some quest title",
        |  "description": "Some description",
        |  "acceptanceCriteria": "Some acceptance criteria"
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
