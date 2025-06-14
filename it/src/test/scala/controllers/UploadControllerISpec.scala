package controllers.user

import cats.effect.*
import controllers.fragments.UserDataControllerFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import fs2.Stream
import io.circe.syntax.*
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import models.*
import models.database.*
import models.database.CreateSuccess
import models.database.DeleteSuccess
import models.database.UpdateSuccess
import models.responses.*
import models.users.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.headers.`Content-Disposition`
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.http4s.Method.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repository.fragments.UserRepoFragments.createUserTable
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*
import org.typelevel.ci._


class UploadControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "POST - /dev-quest-service/upload - should allow the user to upload a valid file"
  ) { (sharedResources, log) =>

    val client = sharedResources._2.client

    // - Create the file part (must be named "file" to match your logic)
    // - File content as stream
    // - Simulated file content
    val fileContent = "This is a test file"
    val fileStream = Stream.emits(fileContent.getBytes(StandardCharsets.UTF_8)).covary[IO]

    // Create the file part
    val filePart = Part[IO](
      headers = Headers(
        `Content-Disposition`("form-data", Map(ci"name" -> "file", ci"filename" -> "test.txt")),
        `Content-Type`(MediaType.text.plain)
      ),
      body = fileStream
    )

    val multipart = Multipart[IO](Vector(filePart))

    val request = Request[IO](
      method = POST,
      uri = uri"http://127.0.0.1:9999/dev-quest-service/v1/upload",
      headers = multipart.headers
    ).withEntity(multipart)

    client.run(request).use { response =>
      response.as[Json].map { body =>
        val keyOpt = body.hcursor.downField("key").as[String]

        expect.all(
          response.status == Status.Ok,
          keyOpt.exists(_.startsWith("uploads/"))
        )
      }
    }
  }

}
