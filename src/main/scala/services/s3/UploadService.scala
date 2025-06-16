package services.s3

import cats.effect.*
import cats.syntax.all.*
import fs2.*
import org.http4s.Uri
import org.typelevel.log4cats.Logger
import software.amazon.awssdk.services.s3.model.*

import java.time.Duration

trait UploadServiceAlgebra[F[_]] {
  def upload(key: String, contentType: String, data: Stream[F, Byte]): F[Unit]
  def generatePresignedUrl(key: String, fileName: String): F[Uri]
  def generatePresignedUploadUrl(key: String): F[Uri]
}

class UploadServiceImpl[F[_] : Async : Logger](
  bucket: String,
  client: S3ClientAlgebra[F],
  presigner: S3PresignerAlgebra[F]
) extends UploadServiceAlgebra[F] {

  override def upload(key: String, contentType: String, data: Stream[F, Byte]): F[Unit] =
    for {
      _ <- Logger[F].info(s"[UploadService] Preparing to upload file to bucket: $bucket, key: $key")
      bytes <- data.compile.to(Array)
      _ <- Logger[F].info(s"[UploadService] Byte array compiled with size: ${bytes.length}")
      _ <- client.putObject(bucket, key, contentType, bytes)
      _ <- Logger[F].info(s"[UploadService] Upload complete to key: $key")
    } yield ()

  override def generatePresignedUrl(key: String, fileName: String): F[Uri] =
    for {
      _ <- Logger[F].info(s"[UploadService] Generating presigned GET URL for key: $key")
      uri <- presigner.presignGetUrl(bucket, key, fileName, Duration.ofMinutes(15))
      _ <- Logger[F].info(s"[UploadService] Generated presigned GET URL: $uri")
    } yield uri

  override def generatePresignedUploadUrl(key: String): F[Uri] =
    for {
      _ <- Logger[F].info(s"[UploadService] Generating presigned PUT URL for key: $key")
      uri <- presigner.presignPutUrl(bucket, key, Duration.ofMinutes(15))
      _ <- Logger[F].info(s"[UploadService] Generated presigned PUT URL: $uri")
    } yield uri
}
