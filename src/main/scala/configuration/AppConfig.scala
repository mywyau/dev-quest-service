package configuration

import cats.kernel.Eq
import configuration.models.*
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

case class AppConfig(
  featureSwitches: FeatureSwitches,
  devSubmission: DevSubmissionConfig,
  localAppConfig: LocalAppConfig,
  integrationSpecConfig: IntegrationSpecConfig
) derives ConfigReader
