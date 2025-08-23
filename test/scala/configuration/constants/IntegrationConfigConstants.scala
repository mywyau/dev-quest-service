package configuration.constants

import configuration.models.*
import configuration.AppConfig

object IntegrationConfigConstants {

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
      bronzeXp = 1000.00,
      ironXp = 2000.00,
      steelXp = 3000.00,
      mithrilXp = 4000.00,
      adamantiteXp = 5000.00,
      runicXp = 6000.00,
      demonicXp = 7000.00,
      ruinXp = 8000.00,
      aetherXp = 10000.00
    )

  val itDevIrlFrontendConfig =
    DevIrlFrontendConfig(
      host = "0.0.0.0",
      port = 3000,
      baseUrl = "http://localhost:3000"
    )

  val estimateConfig =
    EstimationConfig(
      localBucketSeconds = 10,
      localMinimumEstimationWindowSeconds = 30,
      prodBucketSeconds = 21600,
      prodMinimumEstimationWindowSeconds = 72000,
      intervalSeconds = 30,
      estimationThreshold = 3,
      maxDailyEstimates = 5
    )

  val pricingPlanConfig =
    PricingPlanConfig(
      cacheTtlMinutes = 60
    )

  val kafkaConfig =
    KafkaConfig(
      bootstrapServers = "localhost:9092",
      clientId = "dev-quest-service",
      acks = "all",
      lingerMs = 5,
      retries = 10,
      topic = KafkaTopicConfig(
        "quest.created.v1",
        "esimtation.finalized.v1"
      )
    )

  val itSpecServerConfig =
    ServerConfig(
      host = "127.0.0.1",
      port = 9999
    )

  val itPostgresqlConfig =
    PostgresqlConfig(
      dbName = "dev_quest_test_db",
      dockerHost = "dev-quest-db-it",
      host = "localhost",
      port = 5431,
      username = "dev_quest_test_user",
      password = "turnip",
      maxPoolSize = 42
    )

  val itRedisConfig =
    RedisConfig(
      dockerHost = "redis-test-container",
      host = "localhost",
      port = 6380
    )

  val itS3Config =
    S3Config(
      awsRegion = "us-east-1",
      bucketName = "dev-submissions",
      dockerName = "localstack",
      host = "localhost",
      port = 4566
    )

  val itStripeConfig =
    StripeConfig(
      registrationRefreshUrl = "http://localhost:3000/dev/stripe/onboarding/refresh",
      registrationReturnUrl = "http://localhost:3000/dev/stripe/onboarding/success",
      paymentSuccessUrl = "http://localhost:3000/payment/success",
      paymentCancelUrl = "http://localhost:3000/payment/error",
      stripeUrl = "https://api.stripe.com/v1",
      platformFeePercent = 2.5
    )

  val integrationSpecConfig =
    IntegrationSpecConfig(
      serverConfig = itSpecServerConfig,
      postgresqlConfig = itPostgresqlConfig,
      redisConfig = itRedisConfig,
      awsS3Config = itS3Config,
      stripeConfig = itStripeConfig
    )

  val integrationAppConfigConstant =
    AppConfig(
      featureSwitches = featureSwitches,
      pricingPlanConfig = pricingPlanConfig,
      devSubmission = devSubmissionConfig,
      kafka = kafkaConfig,
      questConfig = questConfig,
      estimationConfig = estimateConfig,
      devIrlFrontendConfig = itDevIrlFrontendConfig,
      serverConfig = itSpecServerConfig,
      postgresqlConfig = itPostgresqlConfig,
      redisConfig = itRedisConfig,
      awsS3Config = itS3Config,
      stripeConfig = itStripeConfig
    )
}
