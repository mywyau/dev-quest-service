package s3

import cats.effect.*
import cats.effect.unsafe.implicits.global
import configuration.BaseAppConfig
import fs2.Stream
import org.http4s.Uri
import services.*
import services.s3.S3ClientAlgebra
import services.s3.S3PresignerAlgebra
import services.s3.UploadService
import services.s3.UploadServiceAlgebra
import services.s3.UploadServiceImpl
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import weaver.SimpleIOSuite

import java.net.URI
import java.time.Duration

object UploadServiceISpec extends SimpleIOSuite with AwsS3ISpecBase with BaseAppConfig {

  val region = Region.US_EAST_1
  val bucket = "test-bucket"
  val endpoint = "http://localstack:4566"
  // val endpoint = "http://localhost:4566"

  def s3ClientsResource(): Resource[IO, (S3AsyncClient, S3Presigner)] = {
    for {
      appConfig <- appConfigResource
      useHttps = appConfig.featureSwitches.useHttpsLocalstack
      endpoint = if (useHttps) "https://localstack:4566" else "http://localhost:4566"
      uri = URI.create(endpoint)
      s3Client <- Resource.fromAutoCloseable(IO.blocking {
        S3AsyncClient.builder()
          .endpointOverride(URI.create(endpoint))
          .credentialsProvider(
            StaticCredentialsProvider.create(
              software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
            )
          )
          .region(region)
          .forcePathStyle(true) //  This fixes the DNS issue
          .build()
      })

      presigner <- Resource.fromAutoCloseable(IO.blocking {
        S3Presigner.builder()
          .endpointOverride(URI.create(endpoint))
          .credentialsProvider(
            StaticCredentialsProvider.create(
              software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
            )
          )
          .region(region)
          // .forcePathStyle(true) //  This fixes the DNS issue
          .build()
    })
    } yield (s3Client, presigner)
  }

  // val s3Client: S3AsyncClient =
  //    S3AsyncClient.builder()
  //   .endpointOverride(URI.create(endpoint))
  //   .credentialsProvider(
  //     StaticCredentialsProvider.create(
  //       software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
  //     )
  //   )
  //   .region(region)
  //   .forcePathStyle(true) //  This fixes the DNS issue
  //   .build()

  // val presigner: S3Presigner = 
  //   S3Presigner.builder()
  //   .endpointOverride(URI.create(endpoint))
  //   .credentialsProvider(
  //     StaticCredentialsProvider.create(
  //       software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
  //     )
  //   )
  //   .region(region)
  //   // .forcePathStyle(true) //  This fixes the DNS issue
  //   .build()


  def uploadServiceResource: Resource[IO, UploadServiceAlgebra[IO]] = {
    for {
      s3ClientsResource <- s3ClientsResource()
      (s3Client, presigner) = s3ClientsResource
      // _ <- Resource.eval(ensureBucketExists(s3Client, bucket))
      uploadService = UploadServiceImpl[IO](
        bucket = bucket,
        client = new S3ClientAlgebra[IO] {
          def putObject(bucket: String, key: String, contentType: String, bytes: Array[Byte]): IO[Unit] =
            IO.fromCompletableFuture(IO {
              val request = PutObjectRequest.builder().bucket(bucket).key(key).build()
              s3Client.putObject(request, software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(bytes))
            }).void
        },
        presigner = new S3PresignerAlgebra[IO] {
          def presignGetUrl(bucket: String, key: String, fileName: String, expiresIn: Duration): IO[Uri] = IO {
            val req = GetObjectRequest.builder().bucket(bucket).key(key).responseContentDisposition(s"""attachment; filename="$fileName"""").build()
            val presign = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder().getObjectRequest(req).signatureDuration(expiresIn).build()
            Uri.unsafeFromString(presigner.presignGetObject(presign).url().toString)
          }
          def presignPutUrl(bucket: String, key: String, expiresIn: Duration): IO[Uri] = IO {
            val req = PutObjectRequest.builder().bucket(bucket).key(key).build()
            val presign = software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder().putObjectRequest(req).signatureDuration(expiresIn).build()
            Uri.unsafeFromString(presigner.presignPutObject(presign).url().toString)
          }
        }
      )
    } yield uploadService
  }

  test("upload and verify via presigned URL") { (sharedResource, log) =>

    uploadServiceResource.use { uploadService =>
      val fileName = "hello.txt"
      val key = "integration-test/hello.txt"
      val contentType = "application/octet-stream"
      val data = "Hello, integration test!".getBytes()
  
      for {
        _ <- uploadService.upload(key, contentType, Stream.emits(data).covary[IO])
        presigned <- uploadService.generatePresignedUrl(key, fileName)
      } yield expect(presigned.renderString.contains(key))
    }
  }
  
}
