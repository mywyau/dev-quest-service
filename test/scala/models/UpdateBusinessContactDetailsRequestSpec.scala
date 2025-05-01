package models

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.business.contact_details.UpdateBusinessContactDetailsRequest
import models.ModelsBaseSpec
import testData.BusinessTestConstants.testUpdateBusinessContactDetailsRequest
import weaver.SimpleIOSuite

object UpdateBusinessContactDetailsRequestSpec extends SimpleIOSuite with ModelsBaseSpec {

  test("UpdateBusinessContactDetailsRequest model encodes correctly to JSON") {

    val jsonResult = testUpdateBusinessContactDetailsRequest.asJson

    val expectedJson =
      """
        |{
        |  "primaryContactFirstName": "Michael",
        |  "primaryContactLastName": "Yau",
        |  "contactEmail": "mike@gmail.com",
        |  "contactNumber": "07402205071",
        |  "websiteUrl": "mikey.com"
        |}
        |""".stripMargin

    val expectedResult: Json = parse(expectedJson).getOrElse(Json.Null)

    val jsonResultPretty = printer.print(jsonResult)
    val expectedResultPretty = printer.print(expectedResult)

    val differences = jsonDiff(jsonResult, expectedResult, expectedResultPretty, jsonResultPretty)

    for {
      _ <- IO {
        if (differences.nonEmpty) {
          println("=== JSON Difference Detected! ===")
          differences.foreach(diff => println(s"- $diff"))
          println("Generated JSON:\n" + jsonResultPretty)
          println("Expected JSON:\n" + expectedResultPretty)
        }
      }
    } yield expect(differences.isEmpty)
  }

}
