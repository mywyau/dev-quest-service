package configuration

import cats.kernel.Eq
import configuration.models.*
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

case class AppConfig(
  featureSwitches: FeatureSwitches,
  pricingPlanConfig: PricingPlanConfig,
  devSubmission: DevSubmissionConfig,
  kafka: KafkaConfig,
  redisConfig: RedisConfig,
  questConfig: QuestConfig,
  estimationConfig: EstimationConfig,
  devIrlFrontendConfig: DevIrlFrontendConfig,
  serverConfig: ServerConfig,
  postgresqlConfig: PostgresqlConfig,
  awsS3Config: S3Config,
  stripeConfig: StripeConfig
  // localAppConfig: LocalAppConfig,
  // prodAppConfig: ProdAppConfig,
  // integrationSpecConfig: IntegrationSpecConfig
) derives ConfigReader
