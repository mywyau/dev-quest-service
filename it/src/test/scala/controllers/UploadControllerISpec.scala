// package controllers

// package controllers

// import cats.effect.IO
// import weaver.SimpleIOSuite
// import org.http4s._
// import org.http4s.implicits._
// import org.http4s.multipart._
// import org.http4s.circe._
// import fs2.Stream
// import io.circe.Json
// import services.UploadServiceAlgebra
// import java.util.UUID
// import org.http4s.client.dsl.io._
// import org.http4s.dsl.io._
// import scala.collection.mutable
// import org.http4s.headers.`Content-Type`
// import org.http4s.MediaType

// object UploadRoutesSpec extends SimpleIOSuite {

//   // Dummy implementation of the UploadService
//   class DummyUploadService extends UploadServiceAlgebra[IO] {
//     val uploads = mutable.Buffer.empty[(String, Array[Byte])]
//     override def upload(key: String, data: Stream[IO, Byte]): IO[Unit] =
//       data.compile.to(Array).map(bytes => uploads.append((key, bytes)))

//     override def generatePresignedUrl(key: String): IO[Uri] =
//       IO.pure(Uri.unsafeFromString(s"https://fake-s3.com/download/$key"))

//     override def generatePresignedUploadUrl(key: String): IO[Uri] =
//       IO.pure(Uri.unsafeFromString(s"https://fake-s3.com/upload/$key"))
//   }

//   test("upload valid file returns 200 and saves upload") {
//     val dummyService = new DummyUploadService
//     val routes = new UploadRoutes[IO](dummyService).routes.orNotFound

//     val content = "print('Hello')"
//     val fileName = "test.scala"
//     val filePart = Part.formData[IO](
//       name = "file",
//       filename = fileName,
//       entity = Stream.emits(content.getBytes).covary[IO],
//       headers = Headers(`Content-Type`(MediaType.text.plain))
//     )

//     val multipart = Multipart[IO](Vector(filePart))
//     val req = Request[IO](Method.POST, uri"/upload")
//       .withEntity(multipart)
//       .withHeaders(multipart.headers)

//     for {
//       res <- routes.run(req)
//       body <- res.as[Json]
//     } yield expect.all(
//       res.status == Status.Ok,
//       body.hcursor.get[String]("key").isRight,
//       dummyService.uploads.nonEmpty,
//       new String(dummyService.uploads.head._2) == content
//     )
//   }

//   test("upload with invalid extension returns 400") {
//     val dummyService = new DummyUploadService
//     val routes = new UploadRoutes[IO](dummyService).routes.orNotFound

//     val filePart = Part.formData[IO](
//       name = "file",
//       filename = "bad.exe",
//       entity = Stream.emits("malware".getBytes).covary[IO],
//       headers = Headers(`Content-Type`(MediaType.application.binary))
//     )

//     val multipart = Multipart[IO](Vector(filePart))
//     val req = Request[IO](Method.POST, uri"/upload")
//       .withEntity(multipart)
//       .withHeaders(multipart.headers)

//     for {
//       res <- routes.run(req)
//       body <- res.as[Json]
//     } yield expect.all(
//       res.status == Status.BadRequest,
//       body.hcursor.get[String]("error").contains("Invalid file type")
//     )
//   }

//   test("presigned upload URL route returns URL") {
//     val dummyService = new DummyUploadService
//     val routes = new UploadRoutes[IO](dummyService).routes.orNotFound

//     val json = Json.obj("key" -> Json.fromString("upload/test.txt"))
//     val req = Request[IO](Method.POST, uri"/s3/presign-upload")
//       .withEntity(json)

//     for {
//       res <- routes.run(req)
//       body <- res.as[Json]
//     } yield expect.all(
//       res.status == Status.Ok,
//       body.hcursor.get[String]("url").exists(_.contains("fake-s3.com/upload"))
//     )
//   }
// }
