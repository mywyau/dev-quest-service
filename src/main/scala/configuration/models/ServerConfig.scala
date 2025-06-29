package configuration.models

import cats.kernel.Eq
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

case class ServerConfig(
  host: String,
  port: Int
) derives ConfigReader
