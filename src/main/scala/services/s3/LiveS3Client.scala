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
