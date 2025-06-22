package models.uploads

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import models.uploads.DevSubmissionMetadata
import models.ModelsBaseSpec
import weaver.SimpleIOSuite

object DevSubmissionMetadataSpec extends SimpleIOSuite with ModelsBaseSpec {

  val testDevSubmissionMetadata =
    DevSubmissionMetadata(
      clientId = "USER001",
      devId = "USER002",
      questId = "QUEST001",
      fileName = "test.scala",
      fileType = "application/octet-stream",
      fileSize = 100,
      s3ObjectKey = "some S3 key",
      bucketName = "dev_submissions"
    )

  test("DevSubmissionMetadata model encodes correctly to JSON") {

    val jsonResult = testDevSubmissionMetadata.asJson

    val expectedJson =
      """
        |{
        |  "clientId": "USER001",
        |  "devId": "USER002",
        |  "questId": "QUEST001",
        |  "fileName": "test.scala",
        |  "fileType": "application/octet-stream",
        |  "fileSize": 100,
        |  "s3ObjectKey": "some S3 key",
        |  "bucketName": "dev_submissions"
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
