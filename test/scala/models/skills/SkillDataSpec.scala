package models.skills

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.Dev
import models.ModelsBaseSpec
import models.skills.SkillData
import weaver.SimpleIOSuite

object SkillDataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testSkillData =
    SkillData(
      devId = "USER001",
      username = "kaiba",
      skill = Testing,
      level = 30,
      xp = 40000.00
    )

  test("SkillData model encodes correctly to JSON") {

    val jsonResult = testSkillData.asJson

    val expectedJson =
      """
        |{
        |  "devId": "USER001",
        |  "level": 30,        
        |  "skill": "Testing",
        |  "username": "kaiba",
        |  "xp": 40000.00
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
