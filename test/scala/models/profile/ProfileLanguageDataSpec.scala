package models.profile

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.languages.Rust
import models.languages.Scala
import models.profile.*
import models.profile.ProfileLanguageData
import models.skills.Questing
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object ProfileLanguageDataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testProfileLanguageData =
    ProfileLanguageData(
      language = Rust,
      languageLevel = 50,
      languageXp = 100000.00
    )

  test("ProfileLanguageData model encodes correctly to JSON") {
    val jsonResult = testProfileLanguageData.asJson

    val expectedJson =
      """
      |{
      |  "language": "Rust",
      |  "languageLevel": 50,
      |  "languageXp": 100000.0
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
