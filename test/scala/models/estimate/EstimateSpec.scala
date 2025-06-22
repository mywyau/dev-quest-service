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
      username = "goku",
      rank = Iron,
      comment = Some("some comment")
    )

  test("Estimate model encodes correctly to JSON") {

    val jsonResult = testEstimate.asJson

    val expectedJson =
      """
        |{
        |  "username" : "goku",
        |  "rank" : "Iron",
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
