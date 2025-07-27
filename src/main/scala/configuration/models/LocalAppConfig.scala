package configuration.models

import cats.kernel.Eq
import configuration.models.ServerConfig
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class LocalAppConfig(
  awsS3Config: S3Config,
  devIrlFrontendConfig: DevIrlFrontendConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  serverConfig: ServerConfig,
  stripeConfig: StripeConfig
) derives ConfigReader
