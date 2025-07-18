package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader
import configuration.models.ServerConfig

case class LocalAppConfig(
  devIrlFrontendConfig: DevIrlFrontendConfig,
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
) derives ConfigReader