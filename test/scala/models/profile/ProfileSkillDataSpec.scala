package models.profile

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import java.time.LocalDateTime
import models.languages.Scala
import models.profile.*
import models.profile.ProfileSkillData
import models.skills.Questing
import models.Iron
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object ProfileSkillDataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testProfileSkillData =
    ProfileSkillData(
      skill = Questing,
      skillLevel = 50,
      skillXp = 100000.00
    )

  test("ProfileSkillData model encodes correctly to JSON") {
    val jsonResult = testProfileSkillData.asJson

    val expectedJson =
      """
      |{
      |  "skill": "Questing",
      |  "skillLevel": 50,
      |  "skillXp": 100000.0
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
