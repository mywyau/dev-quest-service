package services.s3

import cats.effect.*
import cats.syntax.all.*
import fs2.*
import org.http4s.Uri
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.net.URI
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

trait UploadServiceAlgebra[F[_]] {

  def upload(key: String, data: Stream[F, Byte]): F[Unit]

  def generatePresignedUrl(key: String): F[Uri]

  def generatePresignedUploadUrl(key: String): F[Uri]

}

class UploadServiceImpl[F[_] : Async](
  bucket: String,
  client: S3ClientAlgebra[F],
  presigner: S3PresignerAlgebra[F]
) extends UploadServiceAlgebra[F] {

// Uploads the file directly to S3:
  override def upload(key: String, data: Stream[F, Byte]): F[Unit] =
    for {
      bytes <- data.compile.to(Array)
      _ <- client.putObject(bucket, key, bytes) // Uses the algebra
    } yield ()

// Allows a client to download the file directly from S3 via a time-limited URL
// Useful for letting the frontend download without needing auth headers
  override def generatePresignedUrl(key: String): F[Uri] =
    presigner.presignGetUrl(bucket, key, Duration.ofMinutes(15)) // Uses the algebra

  // Allows the frontend to upload directly to S3 without proxying through your backend
  override def generatePresignedUploadUrl(key: String): F[Uri] =
    presigner.presignPutUrl(bucket, key, Duration.ofMinutes(15))
}
