package models.uploads

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class DevSubmissionMetadata(
  clientId: String,
  devId: String,
  questId: String,
  fileName: String,
  fileType: String,
  fileSize: Int,
  s3ObjectKey: String,
  bucketName: String
)

object DevSubmissionMetadata {
  implicit val encoder: Encoder[DevSubmissionMetadata] = deriveEncoder[DevSubmissionMetadata]
  implicit val decoder: Decoder[DevSubmissionMetadata] = deriveDecoder[DevSubmissionMetadata]
}
