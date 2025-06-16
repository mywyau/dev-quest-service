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


trait S3PresignerAlgebra[F[_]] {

  def presignGetUrl(bucket: String, key: String, filename: String, expiresIn: Duration): F[Uri]

  def presignPutUrl(bucket: String, key: String, expiresIn: Duration): F[Uri]

}

class LiveS3Presigner[F[_] : Sync](presigner: S3Presigner) extends S3PresignerAlgebra[F] {
  override def presignGetUrl(bucket: String, key: String, filename: String, expiresIn: Duration): F[Uri] = Sync[F].delay {
    val getRequest = GetObjectRequest
      .builder()
      .bucket(bucket)
      .key(key)
      // .responseContentDisposition(s"""attachment; filename="${key.split("/").lastOption.getOrElse("download")}"""") // Or pass filename in
      .responseContentDisposition(s"""attachment; filename="$filename"""")
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
