package controllers

import cats.effect.*
import cats.syntax.all.*
import fs2.Pipe
import fs2.RaiseThrowable
import fs2.Stream
import io.circe.Json
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.multipart.*
import services.UploadServiceAlgebra

import java.util.UUID

class UploadRoutes[F[_] : Async](
  uploadService: UploadServiceAlgebra[F]
) extends Http4sDsl[F] {

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
