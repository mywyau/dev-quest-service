package models

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.ModelsBaseSpec
import testData.BusinessTestConstants.testBusinessContactDetails
import weaver.SimpleIOSuite

object BusinessContactDetailsSpec extends SimpleIOSuite with ModelsBaseSpec {

  test("BusinessContactDetails model encodes correctly to JSON") {

    val jsonResult = testBusinessContactDetails.asJson

    val expectedJson =
      """
        |{
        |  "id": 1,
        |  "userId": "userId1",
        |  "businessId": "businessId1",
        |  "businessName": "businessName1",
        |  "primaryContactFirstName": "Michael",
        |  "primaryContactLastName": "Yau",
        |  "contactEmail": "mike@gmail.com",
        |  "contactNumber": "07402205071",
        |  "websiteUrl": "mikey.com",
        |  "createdAt": "2025-01-01T00:00:00",
        |  "updatedAt": "2025-01-01T00:00:00"
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
