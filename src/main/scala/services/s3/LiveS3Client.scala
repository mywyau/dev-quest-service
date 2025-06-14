package services.s3

import cats.effect.*
import cats.syntax.all.*
import fs2.*
import org.http4s.Uri
import org.typelevel.log4cats.Logger
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*

import java.net.URI
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

trait S3ClientAlgebra[F[_]] {
  def putObject(bucket: String, key: String, contentType:String, content: Array[Byte]): F[Unit]
}

class LiveS3Client[F[_]: Async: Logger](client: S3AsyncClient) extends S3ClientAlgebra[F] {
  override def putObject(bucket: String, key: String, contentType: String, content: Array[Byte]): F[Unit] = {
    val request = PutObjectRequest
      .builder()
      .bucket(bucket)
      .key(key)
      .contentLength(content.length.toLong)
      .contentType(contentType)
      .build()

    val body = AsyncRequestBody.fromBytes(content)

    for {
      _ <- Logger[F].info(s"[LiveS3Client] Attempting to upload to bucket='$bucket', key='$key', size=${content.length} bytes")
      result <- Async[F]
        .fromCompletableFuture(Async[F].delay {
          client.putObject(request, body)
        })
        .attempt
      _ <- result match {
        case Right(_) =>
          Logger[F].info(s"[LiveS3Client] Successfully uploaded key='$key' to bucket='$bucket'")
        case Left(e) =>
          Logger[F].error(e)(s"[LiveS3Client] Failed to upload key='$key' to bucket='$bucket'")
            *> e.raiseError[F, Unit]
      }
    } yield ()
  }
}
