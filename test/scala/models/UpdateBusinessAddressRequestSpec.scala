package models

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.business.address.UpdateBusinessAddressRequest
import models.ModelsBaseSpec
import testData.BusinessTestConstants.testUpdateBusinessAddressRequest
import weaver.SimpleIOSuite

object UpdateBusinessAddressRequestSpec extends SimpleIOSuite with ModelsBaseSpec {

  test("UpdateBusinessAddressRequest model encodes correctly to JSON") {

    val jsonResult = testUpdateBusinessAddressRequest.asJson

    val expectedJson =
      """
        |{
        |  "buildingName": "butter building",
        |  "floorNumber": "floor 1",
        |  "street": "Main street 123",
        |  "city": "New York",
        |  "country": "USA",
        |  "county": "County 123",
        |  "postcode": "123456",
        |  "latitude": 100.1,
        |  "longitude": -100.1
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
