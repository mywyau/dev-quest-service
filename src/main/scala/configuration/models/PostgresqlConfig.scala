package configuration.models

import cats.kernel.Eq
import configuration.models.*
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class PostgresqlConfig(
  dbName: String,
  dockerHost: String,
  host: String,
  port: Int,
  username: String,
  password: String
) derives ConfigReader