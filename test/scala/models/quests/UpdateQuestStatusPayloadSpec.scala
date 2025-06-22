package models.quests

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.quests.UpdateQuestStatusPayload
import models.Failed
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object UpdateQuestStatusPayloadSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testUpdatedRequest =
    UpdateQuestStatusPayload(
      questStatus = Failed
    )

  test("UpdateQuestStatusPayload model encodes correctly to JSON") {

    val jsonResult = testUpdatedRequest.asJson

    val expectedJson =
      """
        |{
        |  "questStatus": "Failed"
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
