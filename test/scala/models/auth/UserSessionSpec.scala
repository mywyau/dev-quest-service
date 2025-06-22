package models.auth

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.Iron
import models.ModelsBaseSpec
import models.auth.UserSession
import models.languages.*
import weaver.SimpleIOSuite

import java.time.LocalDateTime

object UserSessionSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testUserSession =
    UserSession(
      userId = "USER001",
      cookieValue = "Some cookie value",
      email = "turnip@gmail.com",
      userType = "Dev"
    )

  test("UserSession model encodes correctly to JSON") {

    val jsonResult = testUserSession.asJson

    val expectedJson =
      """
        |{
        |  "userId" : "USER001",
        |  "cookieValue" : "Some cookie value",
        |  "email" : "turnip@gmail.com",
        |  "userType" : "Dev"
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
