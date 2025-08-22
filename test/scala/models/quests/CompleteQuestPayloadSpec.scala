package models.quests

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.quests.CompleteQuestPayload
import models.Demonic
import models.InProgress
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object CompleteQuestPayloadSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testAcceptPayload =
    CompleteQuestPayload(
      rank = Demonic,
      questStatus = InProgress
    )

  test("CompleteQuestPayload model encodes correctly to JSON") {

    val jsonResult = testAcceptPayload.asJson

    val expectedJson =
      """
        |{
        |  "rank": "Demonic",
        |  "questStatus": "InProgress"
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
