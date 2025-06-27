package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class S3Config(
  awsRegion: String,
  bucketName: String,
  dockerName: String,
  host: String,
  port: Int
) derives ConfigReader
