package configuration

import configuration.models.*

import configuration.AppConfig

object AppConfigConstants {

  val featureSwitches =
    FeatureSwitches(
      useDockerHost = false,
      localTesting = false,
      useCors = false,
      useHttpsLocalstack = false,
      useProdStripe = false
    )

  val devIrlFrontendConfig =
    DevIrlFrontendConfig(
      host = "0.0.0.0",
      port = 8080,
      baseUrl = ""
    )

  val appServerConfig =
    ServerConfig(
      host = "0.0.0.0",
      port = 8080
    )

  val containerPostgreSqlConfig =
    PostgresqlConfig(
      dbName = "dev_quest_db",
      dockerHost = "dev-quest-container",
      host = "localhost",
      port = 5432,
      username = "dev_quest_user",
      password = "turnip"
    )

  val redisConfig =
    RedisConfig(
      dockerHost = "redis-container",
      host = "localhost",
      port = 6379
    )

  val s3Config =
    S3Config(
      awsRegion = "us-east-1",
      bucketName = "dev-submissions",
      dockerName = "localstack",
      host = "localhost",
      port = 4566
    )

  val stripeConfig =
    StripeConfig(
      registrationRefreshUrl = "http://localhost:3000/dev/stripe/onboarding/refresh",
      registrationReturnUrl = "http://localhost:3000/dev/stripe/onboarding/success",
      paymentSuccessUrl = "http://localhost:3000/payment/success",
      paymentCancelUrl = "http://localhost:3000/payment/error",
      stripeUrl = "https://api.stripe.com/v1",
      platformFeePercent = 2.5
    )

  val devSubmissionConfig =
    DevSubmissionConfig(
      expiryDays = 730
    )

  val localAppConfig =
    LocalAppConfig(
      devIrlFrontendConfig = devIrlFrontendConfig,
      serverConfig = appServerConfig,
      postgresqlConfig = containerPostgreSqlConfig,
      redisConfig = redisConfig,
      awsS3Config = s3Config,
      stripeConfig = stripeConfig
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
      password = "turnip"
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

  val integrationSpecConfig =
    IntegrationSpecConfig(
      serverConfig = itSpecServerConfig,
      postgresqlConfig = itPostgresqlConfig,
      redisConfig = itRedisConfig,
      awsS3Config = itS3Config,
      stripeConfig = stripeConfig
    )

  val appConfig =
    AppConfig(
      featureSwitches = featureSwitches,
      devSubmission = devSubmissionConfig,
      localAppConfig = localAppConfig,
      integrationSpecConfig = integrationSpecConfig
    )

}
