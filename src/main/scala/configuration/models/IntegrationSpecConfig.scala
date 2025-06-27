package configuration.models

import cats.kernel.Eq
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

import configuration.models.ServerConfig

case class IntegrationSpecConfig(
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
) derives ConfigReader
