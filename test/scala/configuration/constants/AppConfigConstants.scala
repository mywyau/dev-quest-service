package configuration.constants

import configuration.AppConfig
import configuration.models.*
import configuration.constants.*
import configuration.constants.IntegrationConfigConstants.*
import configuration.constants.LocalAppConfigConstants.*
import configuration.constants.ProdAppConfigConstants.*

object AppConfigConstants {

  val featureSwitches =
    FeatureSwitches(
      useDockerHost = false,
      localTesting = false,
      useCors = false,
      useHttpsLocalstack = false,
      useProdStripe = false
    )

  val devSubmissionConfig =
    DevSubmissionConfig(
      expiryDays = 730
    )

  val appConfig =
    AppConfig(
      featureSwitches = featureSwitches,
      devSubmission = devSubmissionConfig,
      localAppConfig = localAppConfig,
      prodAppConfig = prodAppConfig,
      integrationSpecConfig = integrationSpecConfig
    )

}
