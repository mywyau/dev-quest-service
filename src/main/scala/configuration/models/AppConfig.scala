package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class DevIrlFrontendConfig(
  host: String,
  port: Int,
  baseUrl: String
)

case class FeatureSwitches(
  useDockerHost: Boolean,
  localTesting: Boolean,
  useCors: Boolean,
  useHttpsLocalstack: Boolean,
  useProdStripe: Boolean
) derives ConfigReader

case class DevSubmissionConfig(
  expiryDays: Int
)

case class StripeConfig(
  registrationRefreshUrl: String,
  registrationReturnUrl: String,
  paymentSuccessUrl: String,
  paymentCancelUrl: String,
  stripeUrl: String,
  platformFeePercent: BigDecimal
) derives ConfigReader

case class S3Config(
  awsRegion: String,
  bucketName: String,
  dockerName: String,
  host: String,
  port: Int
) derives ConfigReader

case class RedisConfig(
  dockerHost: String,
  host: String,
  port: Int
) derives ConfigReader

case class ServerConfig(
  host: String,
  port: Int
) derives ConfigReader

case class PostgresqlConfig(
  dbName: String,
  dockerHost: String,
  host: String,
  port: Int,
  username: String,
  password: String
) derives ConfigReader

case class LocalAppConfig(
  devIrlFrontendConfig: DevIrlFrontendConfig,
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
) derives ConfigReader

case class ProdAppConfig(
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
) derives ConfigReader

case class IntegrationSpecConfig(
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  redisConfig: RedisConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
) derives ConfigReader

case class AppConfig(
  featureSwitches: FeatureSwitches,
  devSubmission: DevSubmissionConfig,
  localAppConfig: LocalAppConfig,
  integrationSpecConfig: IntegrationSpecConfig
) derives ConfigReader
