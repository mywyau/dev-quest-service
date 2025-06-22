package models.users

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.Dev
import models.ModelsBaseSpec
import models.users.UpdateUserData
import weaver.SimpleIOSuite

object UpdateUserDataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testUpdateUserData =
    UpdateUserData(
      email = "USER001",
      firstName = Some("bob"),
      lastName = Some("smith"),
      userType = Some(Dev)
    )

  test("UpdateUserData model encodes correctly to JSON") {

    val jsonResult = testUpdateUserData.asJson

    val expectedJson =
      """
        |{
        |  "email": "USER001",
        |  "firstName": "bob",
        |  "lastName": "smith",
        |  "userType": "Dev"
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
