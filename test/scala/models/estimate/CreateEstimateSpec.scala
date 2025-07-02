package models.estimate

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.estimate.CreateEstimate
import models.languages.*
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object CreateEstimateSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testCreateEstimate =
    CreateEstimate(
      questId = "QUEST001",
      score = 50,
      days = 8,
      comment = Some("some comment")
    )

  test("CreateEstimate model encodes correctly to JSON") {

    val jsonResult = testCreateEstimate.asJson

    val expectedJson =
      """
        |{
        |  "questId" : "QUEST001",
        |  "score" : 50,
        |  "days" : 8,
        |  "comment" : "some comment"
        |}
        |""".stripMargin

    val expectedResult: Json = parse(expectedJson).getOrElse(Json.Null)

    val jsonResultPretty: String = printer.print(jsonResult)
    val expectedResultPretty: String = printer.print(expectedResult)

    val differences: List[String] = jsonDiff(jsonResult, expectedResult, expectedResultPretty, jsonResultPretty)

    for {
      _ <- IO {
        if (differences.nonEmpty) {
          diffPrinter(differences, jsonResultPretty, expectedResultPretty)
        }
      }
    } yield expect(differences.isEmpty)
  }

}
