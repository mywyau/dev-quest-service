package models.users

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.Dev
import models.ModelsBaseSpec
import models.users.RegistrationUserDataPartial
import weaver.SimpleIOSuite

object RegistrationUserDataPartialSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testRegistrationUserDataPartial =
    RegistrationUserDataPartial(
      userId = "USER001",
      email = "bob_smith@gmail.com",
      firstName = Some("bob"),
      lastName = Some("smith"),
      userType = Some(Dev)
    )

  test("RegistrationUserDataPartial model encodes correctly to JSON") {

    val jsonResult = testRegistrationUserDataPartial.asJson

    val expectedJson =
      """
        |{
        |  "userId" : "USER001",
        |  "email" : "bob_smith@gmail.com",
        |  "firstName" : "bob",
        |  "lastName" : "smith",
        |  "userType" : "Dev"
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
