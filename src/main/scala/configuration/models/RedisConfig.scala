package configuration.models

import cats.kernel.Eq
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

import configuration.models.*

case class RedisConfig(
  dockerHost: String,
  host: String,
  port: Int
) derives ConfigReader
