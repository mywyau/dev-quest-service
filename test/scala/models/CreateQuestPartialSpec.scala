package models

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.quests.CreateQuestPartial
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object CreateQuestPartialSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testCreatedRequest =
    CreateQuestPartial(
      title = "Some quest title",
      description = Some("Some description")
    )

  test("CreateQuestPartial model encodes correctly to JSON") {

    val jsonResult = testCreatedRequest.asJson

    val expectedJson =
      """
        |{
        |  "title": "Some quest title",
        |  "description": "Some description"
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
