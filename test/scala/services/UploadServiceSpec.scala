import cats.effect.IO
import fs2.Stream
import java.time.Duration
import org.http4s.Uri
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import services.s3.S3ClientAlgebra
import services.s3.S3PresignerAlgebra
import services.s3.UploadServiceImpl
import weaver.SimpleIOSuite

object UploadServiceSpec extends SimpleIOSuite {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  class MockS3Client extends S3ClientAlgebra[IO] {

    var uploaded: Option[(String, String, String, Array[Byte])] = None

    override def putObject(bucket: String, key: String, contentType: String, content: Array[Byte]): IO[Unit] =
      IO { uploaded = Some((bucket, key, contentType, content)) }
  }

  class MockPresigner extends S3PresignerAlgebra[IO] {

    override def presignGetUrl(bucket: String, key: String, expiresIn: Duration): IO[Uri] =
      IO.pure(Uri.unsafeFromString(s"https://mock-s3/$bucket/$key"))

    override def presignPutUrl(bucket: String, key: String, expiresIn: Duration): IO[Uri] = ???
  }

  test("upload should delegate to S3Client") {
    val s3 = new MockS3Client
    val presigner = new MockPresigner
    val service = new UploadServiceImpl[IO]("test-bucket", s3, presigner)

    val data = "hello".getBytes()
    val stream = Stream.emits(data).covary[IO]

    for {
      _ <- service.upload("foo.txt", "application/octet-stream", stream)
      result <- IO(s3.uploaded)
    } yield result match {
      case Some((bucket, key, contentType, bytes)) =>
        expect.eql(bucket, "test-bucket") and
          expect.eql(key, "foo.txt") and
          expect.eql(bytes.toSeq, data.toSeq) // ✅ compare contents, not reference
      case None =>
        failure("Expected some uploaded data, got None")
    }
  }

  test("generatePresignedUrl should use S3PresignerAlgebra") {
    val s3 = new MockS3Client
    val presigner = new MockPresigner
    val service = new UploadServiceImpl[IO]("test-bucket", s3, presigner)

    service.generatePresignedUrl("foo.txt").map { uri =>
      expect.eql(uri.renderString, "https://mock-s3/test-bucket/foo.txt")
    }
  }
}
