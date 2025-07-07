package configuration.constants

import configuration.constants.*
import configuration.constants.IntegrationConfigConstants.*
import configuration.constants.LocalAppConfigConstants.*
import configuration.constants.ProdAppConfigConstants.*
import configuration.models.*
import configuration.AppConfig

object AppConfigConstants {

  val featureSwitches =
    FeatureSwitches(
      useDockerHost = false,
      localTesting = false,
      useCors = false,
      useHttpsLocalstack = true,
      useProdStripe = false
    )

  val devSubmissionConfig =
    DevSubmissionConfig(
      expiryDays = 730
    )

  val questConfig =
    QuestConfig(
      maxActiveQuests = 5,
      bronzeXp = 100.00,
      ironXp = 300.00,
      steelXp = 700.00,
      mithrilXp = 1000.00,
      adamantiteXp = 3000.00,
      runicXp = 5000.00,
      demonXp = 7000.00,
      ruinousXp = 8000.00,
      aetherXp = 10000.00
    )

  val estimateConfig =
    EstimationConfig(
      estimationThreshold = 5
    )

  val appConfig =
    AppConfig(
      featureSwitches = featureSwitches,
      devSubmission = devSubmissionConfig,
      questConfig = questConfig,
      estimationConfig = estimateConfig,
      localAppConfig = localAppConfig,
      prodAppConfig = prodAppConfig,
      integrationSpecConfig = integrationSpecConfig
    )

}
