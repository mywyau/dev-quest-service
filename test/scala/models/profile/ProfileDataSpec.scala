package models.profile

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.Iron
import models.ModelsBaseSpec
import models.languages.Scala
import models.profile.*
import models.profile.ProfileData
import models.skills.Questing
import weaver.SimpleIOSuite

import java.time.LocalDateTime

object ProfileDataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testProfileData =
    ProfileData(
      devId = "USER001",
      username = Some("goku"),
      skillData = List(
        ProfileSkillData(
          skill = Questing,
          skillLevel = 50,
          skillXp = 100000.00,
          nextLevel = 51,
          nextLevelXp = 120000.00
        )
      ),
      languageData = List(
        ProfileLanguageData(
          language = Scala,
          languageLevel = 50,
          languageXp = 100000.00,
          nextLevel = 51,
          nextLevelXp = 120000.00
        )
      )
    )

  test("ProfileData model encodes correctly to JSON") {
    val jsonResult = testProfileData.asJson

    val expectedJson =
      """
      |{
      |  "devId": "USER001",
      |  "username": "goku",
      |  "skillData": [
      |    {
      |      "skill": "Questing",
      |      "skillLevel": 50,
      |      "skillXp": 100000.0,
      |      "nextLevel": 51,
      |      "nextLevelXp": 120000.0
      |    }
      |  ],
      |  "languageData": [
      |    {
      |      "language": "Scala",
      |      "languageLevel": 50,
      |      "languageXp": 100000.0,
      |      "nextLevel": 51,
      |      "nextLevelXp": 120000.0
      |    }
      |  ]
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
