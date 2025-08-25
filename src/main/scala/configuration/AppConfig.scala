package configuration

import cats.kernel.Eq
import configuration.models.*
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class AppConfig(
  featureSwitches: FeatureSwitches,
  pricingPlanConfig: PricingPlanConfig,
  devSubmission: DevSubmissionConfig,
  kafka: KafkaConfig,
  questConfig: QuestConfig,
  estimationConfig: EstimationConfig,
  localAppConfig: LocalAppConfig,
  prodAppConfig: ProdAppConfig,
  integrationSpecConfig: IntegrationSpecConfig
) derives ConfigReader
