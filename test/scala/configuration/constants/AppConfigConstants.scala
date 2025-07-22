package configuration.constants

import configuration.AppConfig
import configuration.constants.*
import configuration.constants.IntegrationConfigConstants.*
import configuration.constants.LocalAppConfigConstants.*
import configuration.constants.ProdAppConfigConstants.*
import configuration.models.*

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
      ironXp = 200.00,
      steelXp = 300.00,
      mithrilXp = 400.00,
      adamantiteXp = 500.00,
      runicXp = 600.00,
      demonXp = 700.00,
      ruinousXp = 800.00,
      aetherXp = 1000.00
    )

  val estimateConfig =
    EstimationConfig(
      estimationThreshold = 3,
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
