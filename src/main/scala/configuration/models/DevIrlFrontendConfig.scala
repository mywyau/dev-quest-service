package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class DevIrlFrontendConfig(
  host: String,
  port: Int,
  baseUrl: String
)