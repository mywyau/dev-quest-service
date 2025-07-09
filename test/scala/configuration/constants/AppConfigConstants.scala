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
      ironXp = 250.00,
      steelXp = 400.00,
      mithrilXp = 700.00,
      adamantiteXp = 1000.00,
      runicXp = 1500.00,
      demonXp = 2100.00,
      ruinousXp = 2700.00,
      aetherXp = 3500.00
    )

  val estimateConfig =
    EstimationConfig(
      estimationThreshold = 5,
      maxDailyReviews = 5
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
