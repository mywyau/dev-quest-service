package controllers

import cats.data.Validated.Valid
import cats.effect.*
import cats.implicits.*
import cats.syntax.all.*
import configuration.AppConfig
import fs2.Pipe
import fs2.RaiseThrowable
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import java.util.UUID
import models.database.CreateSuccess
import models.responses.ErrorResponse
import models.responses.GetResponse
import models.uploads.DevSubmissionMetadata
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.*
import org.typelevel.log4cats.Logger
import services.s3.UploadServiceAlgebra
import services.DevSubmissionServiceAlgebra

trait UploadControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class UploadController[F[_] : Async : Logger](
  uploadService: UploadServiceAlgebra[F],
  devSubmissionService: DevSubmissionServiceAlgebra[F],
  appConfig: AppConfig
) extends UploadControllerAlgebra[F]
    with Http4sDsl[F] {

  val maxSize: Long = 20 * 1024 * 1024 // 5MB
  // val allowedExtensions = Set(".scala", ".py", ".js", ".ts", ".java", ".txt", ".png")

  // Utility to enforce size limit
  def limitSize[F[_] : RaiseThrowable](max: Long): Pipe[F, Byte, Byte] = { in =>
    in.chunkLimit(max.toInt + 1).flatMap { chunk =>
      if (chunk.size > max)
        Stream.raiseError[F](new Exception("File too large"))
      else
        Stream.chunk(chunk)
    }
  }

  // Extract text field from multipart part, fallback to empty string if missing or failed
  private def extractField(name: String, multipart: Multipart[F]): F[String] =
    multipart.parts.find(_.name.contains(name)) match {
      case Some(part) =>
        part.bodyText.compile.string.handleError(_ => "").map(_.trim)
      case None => Async[F].pure("")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "upload" / "health" =>
      Logger[F].debug(s"[UploadController] GET - Health check for backend service: ${GetResponse("success", "I am alive").asJson}") *>
        Ok(GetResponse("successful uploads health route", "I am alive").asJson)

    case GET -> Root / "dev" / "submission" / "file" / "metadata" / questId =>
      for {
        _ <- Logger[F].debug(s"[UploadController] GET - Fetching file metadata for questId: $questId")
        metadataList <- devSubmissionService.getAllFileMetaData(questId)
        response <-
          if (metadataList.nonEmpty)
            Ok(metadataList.asJson)
          else
            BadRequest(ErrorResponse("NO_METADATA", "No file metadata found").asJson)
      } yield response

    case req @ POST -> Root / "v1" / "upload" =>
      for {
        multipart <- req.as[Multipart[F]]
        result <- multipart.parts.find(_.name.contains("file")) match {
          case Some(filePart) =>
            val filename = filePart.filename.getOrElse("upload.txt")
            // val extensionOk = allowedExtensions.exists(filename.endsWith)

            // if (!extensionOk) {
            //   BadRequest(Json.obj("error" -> Json.fromString("Invalid file type")))
            // } else {
            val objectKey = s"uploads/${UUID.randomUUID().toString}-$filename"

            // Validate file size
            val sizeCheckStream = filePart.body.through(limitSize(maxSize))

            val contentType = filePart.headers
              .get[`Content-Type`]
              .map(_.mediaType.toString)
              .getOrElse("application/octet-stream")

            // Upload to S3
            uploadService
              .upload(objectKey, contentType, sizeCheckStream)
              .flatMap { _ =>
                Logger[F].debug(s"Uploaded file to S3 with key: $objectKey") *>
                  Ok(Json.obj("key" -> Json.fromString(objectKey)))
              }
              .handleErrorWith { e =>
                InternalServerError(Json.obj("error" -> Json.fromString(e.getMessage)))
              }

          case None =>
            BadRequest(Json.obj("error" -> Json.fromString("Missing file part")))
        }
      } yield result

    case req @ POST -> Root / "v2" / "upload" =>
      for {
        multipart <- req.as[Multipart[F]]
        maybeFilePart = multipart.parts.find(_.name.contains("file"))
        clientId <- extractField("clientId", multipart)
        devId <- extractField("devId", multipart)
        questId <- extractField("questId", multipart)
        result <- maybeFilePart match {
          case Some(filePart) =>
            val filename = filePart.filename.getOrElse("upload.txt")
            val extension = filename.split('.').lastOption.getOrElse("")
            // val extensionOk = allowedExtensions.exists(filename.endsWith)

            // if (!extensionOk) {
            //   BadRequest(Json.obj("error" -> Json.fromString("Invalid file type")))
            // } else {
            val objectKey = s"uploads/${UUID.randomUUID().toString}-$filename"
            val sizeCheckStream = filePart.body.through(limitSize(maxSize))

            val contentType = filePart.headers
              .get[`Content-Type`]
              .map(_.mediaType.toString)
              .getOrElse("application/octet-stream")

            for {
              content <- filePart.body.compile.to(Array)
              _ <- uploadService.upload(objectKey, contentType, Stream.emits(content).covary[F])
              _ <- devSubmissionService.createFileMetaData(
                DevSubmissionMetadata(
                  clientId = clientId,
                  devId = devId,
                  questId = questId,
                  fileName = filename,
                  fileType = contentType,
                  fileSize = content.length,
                  s3ObjectKey = objectKey,
                  bucketName = appConfig.localAppConfig.awsS3Config.bucketName
                )
              )
              _ <- Logger[F].debug(s"Uploaded and saved metadata for $filename")
              res <- Ok(Json.obj("key" -> Json.fromString(objectKey)))
            } yield res

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

    case req @ POST -> Root / "s3" / "presign-download" =>
      for {
        json <- req.as[Json]
        s3ObjectKey <- Async[F].fromEither(json.hcursor.get[String]("key").leftMap(e => new Exception(e.message)))
        metaData <- devSubmissionService.getFileMetaData(s3ObjectKey)
        url <- uploadService.generatePresignedUrl(s3ObjectKey, metaData.map(_.fileName).getOrElse("download"))
        res <- Ok(Json.obj("url" -> Json.fromString(url.renderString)))
      } yield res
  }

}
