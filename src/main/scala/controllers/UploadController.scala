package controllers

import cats.data.Validated.Valid
import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import fs2.Pipe
import fs2.RaiseThrowable
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import java.util.UUID
import models.database.CreateSuccess
import models.responses.GetResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.multipart.*
import org.typelevel.log4cats.Logger
import services.s3.UploadServiceAlgebra

trait UploadControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class UploadController[F[_] : Async : Logger](
  uploadService: UploadServiceAlgebra[F]
) extends UploadControllerAlgebra[F]
    with Http4sDsl[F] {

  val maxSize: Long = 5 * 1024 * 1024 // 5MB
  val allowedExtensions = Set(".scala", ".py", ".js", ".ts", ".java", ".txt")

  // Utility to enforce size limit
  def limitSize[F[_] : RaiseThrowable](max: Long): Pipe[F, Byte, Byte] = { in =>
    in.chunkLimit(max.toInt + 1).flatMap { chunk =>
      if (chunk.size > max)
        Stream.raiseError[F](new Exception("File too large"))
      else
        Stream.chunk(chunk)
    }
  }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "uploads" / "health" =>
      Logger[F].info(s"[UploadController] GET - Health check for backend service: ${GetResponse("success", "I am alive").asJson}") *>
        Ok(GetResponse("successful uploads health route", "I am alive").asJson)
    case req @ POST -> Root / "upload" =>
      for {
        multipart <- req.as[Multipart[F]]
        result <- multipart.parts.find(_.name.contains("file")) match {
          case Some(filePart) =>
            val filename = filePart.filename.getOrElse("upload.txt")
            val extensionOk = allowedExtensions.exists(filename.endsWith)

            if (!extensionOk) {
              BadRequest(Json.obj("error" -> Json.fromString("Invalid file type")))
            } else {
              val objectKey = s"uploads/${UUID.randomUUID().toString}-$filename"

              // Validate file size
              val sizeCheckStream = filePart.body.through(limitSize(maxSize))

              // Upload to S3
              uploadService
                .upload(objectKey, sizeCheckStream)
                .flatMap { _ =>
                  Logger[F].info(s"Uploaded file to S3 with key: $objectKey") *>
                    Ok(Json.obj("key" -> Json.fromString(objectKey)))
                }
                .handleErrorWith { e =>
                  InternalServerError(Json.obj("error" -> Json.fromString(e.getMessage)))
                }
            }

          case None =>
            BadRequest(Json.obj("error" -> Json.fromString("Missing file part")))
        }
      } yield result

    case req @ POST -> Root / "s3" / "presign-upload" =>
      for {
        json <- req.as[Json]
        key <- Async[F].pure(json.hcursor.get[String]("key").getOrElse("default.txt"))
        url <- uploadService.generatePresignedUploadUrl(key)
        res <- Ok(Json.obj("url" -> Json.fromString(url.renderString)))
      } yield res

  }

}
