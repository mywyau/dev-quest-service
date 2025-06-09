package services

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

trait S3ClientAlgebra[F[_]] {
  def putObject(bucket: String, key: String, content: Array[Byte]): F[Unit]
}

class LiveS3Client[F[_] : Async](client: S3AsyncClient) extends S3ClientAlgebra[F] {
  override def putObject(bucket: String, key: String, content: Array[Byte]): F[Unit] = {
    val request = PutObjectRequest
      .builder()
      .bucket(bucket)
      .key(key)
      .contentLength(content.length.toLong)
      .contentType("application/octet-stream")
      .build()

    val body = AsyncRequestBody.fromBytes(content)

    Async[F]
      .fromCompletableFuture(Async[F].delay {
        client.putObject(request, body)
      })
      .void
  }
}

trait S3PresignerAlgebra[F[_]] {

  def presignGetUrl(bucket: String, key: String, expiresIn: Duration): F[Uri]

  def presignPutUrl(bucket: String, key: String, expiresIn: Duration): F[Uri]

}

class LiveS3Presigner[F[_] : Sync](presigner: S3Presigner) extends S3PresignerAlgebra[F] {
  override def presignGetUrl(bucket: String, key: String, expiresIn: Duration): F[Uri] = Sync[F].delay {
    val getRequest = GetObjectRequest
      .builder()
      .bucket(bucket)
      .key(key)
      .build()

    val presignRequest = GetObjectPresignRequest
      .builder()
      .getObjectRequest(getRequest)
      .signatureDuration(expiresIn)
      .build()

    val presigned = presigner.presignGetObject(presignRequest)
    Uri.unsafeFromString(presigned.url().toString)
  }

  override def presignPutUrl(bucket: String, key: String, expiresIn: Duration): F[Uri] =
    Sync[F].delay {
      val request = PutObjectRequest.builder().bucket(bucket).key(key).build()
      val presignRequest = software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
        .builder()
        .putObjectRequest(request)
        .signatureDuration(expiresIn)
        .build()

      val presigned = presigner.presignPutObject(presignRequest)
      Uri.unsafeFromString(presigned.url().toString)
    }
}

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

  override def upload(key: String, data: Stream[F, Byte]): F[Unit] =
    for {
      bytes <- data.compile.to(Array)
      _ <- client.putObject(bucket, key, bytes) // Uses the algebra
    } yield ()

  override def generatePresignedUrl(key: String): F[Uri] =
    presigner.presignGetUrl(bucket, key, Duration.ofMinutes(15)) // Uses the algebra

  override def generatePresignedUploadUrl(key: String): F[Uri] = // ✅ new
    presigner.presignPutUrl(bucket, key, Duration.ofMinutes(15))
}
