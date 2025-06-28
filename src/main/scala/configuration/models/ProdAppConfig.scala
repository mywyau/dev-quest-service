package configuration.models

import cats.kernel.Eq
import configuration.models.ServerConfig
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class ProdAppConfig(
  devIrlFrontendConfig: DevIrlFrontendConfig,
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
) derives ConfigReader
