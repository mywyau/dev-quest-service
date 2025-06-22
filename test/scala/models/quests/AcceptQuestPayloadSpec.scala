package models.quests

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.ModelsBaseSpec
import models.quests.AcceptQuestPayload
import weaver.SimpleIOSuite

object AcceptQuestPayloadSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testAcceptPayload =
    AcceptQuestPayload(
      devId = "USER001",
      questId = "QUEST001"
    )

  test("AcceptQuestPayload model encodes correctly to JSON") {

    val jsonResult = testAcceptPayload.asJson

    val expectedJson =
      """
        |{
        |  "devId": "USER001",
        |  "questId": "QUEST001"
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
