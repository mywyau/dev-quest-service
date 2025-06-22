package models.languages

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.languages.LanguageData
import models.languages.*
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object LanguageDataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testLanguageData =
    LanguageData(
      devId = "USER001",
      username = "goku",
      language = Python,
      level = 4,
      xp = 1000.00
    )

  test("LanguageData model encodes correctly to JSON") {

    val jsonResult = testLanguageData.asJson

    val expectedJson =
      """
        |{
        |  "devId" : "USER001",
        |  "username" : "goku",
        |  "language" : "Python",
        |  "level" : 4,
        |  "xp" : 1000.00
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
