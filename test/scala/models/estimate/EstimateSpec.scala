package models.estimate

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.estimate.Estimate
import models.estimate.Estimate
import models.languages.*
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object EstimateSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testEstimate =
    Estimate(
      devId = "dev123",
      username = "goku",
      score = 50,
      hours = 8.5,
      comment = Some("a comment")
    )

  test("Estimate model encodes correctly to JSON") {

    val jsonResult = testEstimate.asJson

    val expectedJson =
      """
        |{
        |  "devId" : "dev123",
        |  "username" : "goku",
        |  "score" : 50,
        |  "hours" : 8.5,
        |  "comment" : "a comment"
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
