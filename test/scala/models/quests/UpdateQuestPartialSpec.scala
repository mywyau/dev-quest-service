package models.quests

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.Iron
import models.ModelsBaseSpec
import models.quests.UpdateQuestPartial
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
          diffPrinter(differences, jsonResultPretty, expectedResultPretty)
        }
      }
    } yield expect(differences.isEmpty)
  }

}
