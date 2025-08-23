// package configuration.constants

// import configuration.constants.*
// import configuration.constants.IntegrationConfigConstants.*
// import configuration.constants.LocalAppConfigConstants.*
// import configuration.constants.ProdAppConfigConstants.*
// import configuration.models.*
// import configuration.AppConfig

// object AppConfigConstants {

//   val featureSwitches =
//     FeatureSwitches(
//       useDockerHost = false,
//       localTesting = false,
//       useCors = false,
//       useHttpsLocalstack = true,
//       useProdStripe = false
//     )

//   val devSubmissionConfig =
//     DevSubmissionConfig(
//       expiryDays = 730
//     )

//   val questConfig =
//     QuestConfig(
//       maxActiveQuests = 5,
//       bronzeXp = 1000.00,
//       ironXp = 2000.00,
//       steelXp = 3000.00,
//       mithrilXp = 4000.00,
//       adamantiteXp = 5000.00,
//       runicXp = 6000.00,
//       demonicXp = 7000.00,
//       ruinXp = 8000.00,
//       aetherXp = 10000.00
//     )

//   val estimateConfig =
//     EstimationConfig(
//       localBucketSeconds = 10,
//       localMinimumEstimationWindowSeconds = 30,
//       prodBucketSeconds = 21600,
//       prodMinimumEstimationWindowSeconds = 72000,
//       intervalSeconds = 30,
//       estimationThreshold = 3,
//       maxDailyEstimates = 5
//     )

//   val pricingPlanConfig =
//     PricingPlanConfig(
//       cacheTtlMinutes = 60
//     )

//   val kafkaConfig =
//     KafkaConfig(
//       bootstrapServers = "localhost:9092",
//       clientId = "dev-quest-service",
//       acks = "all",
//       lingerMs = 5,
//       retries = 10,
//       topic = KafkaTopicConfig(
//         "quest.created.v1",
//         "esimtation.finalized.v1"
//       )
//     )

//   val appConfigConstant =
//     AppConfig(
//       featureSwitches = featureSwitches,
//       pricingPlanConfig = pricingPlanConfig,
//       devSubmission = devSubmissionConfig,
//       kafka = kafkaConfig,
//       questConfig = questConfig,
//       estimationConfig = estimateConfig,
//       devIrlFrontendConfig = devIrlFrontendConfig,
//       serverConfig = appServerConfig,
//       postgresqlConfig = containerPostgreSqlConfig,
//       redisConfig = redisConfig,
//       awsS3Config = s3Config,
//       stripeConfig = stripeConfig
//     )

// }
