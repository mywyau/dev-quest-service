package configuration.constants

import configuration.models.*

object ProdAppConfigConstants {

  val devIrlFrontendConfig =
    DevIrlFrontendConfig(
      host = "0.0.0.0",
      port = 8080,
      baseUrl = "https://devirl.com"
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
      registrationRefreshUrl = "https://devirl.com/dev/stripe/onboarding/refresh",
      registrationReturnUrl = "https://devirl.com/dev/stripe/onboarding/success",
      paymentSuccessUrl = "https://devirl.com/payment/success",
      paymentCancelUrl = "https://devirl.com/payment/error",
      stripeUrl = "https://api.stripe.com/v1",
      platformFeePercent = 2.5
    )

  val prodAppConfig =
    ProdAppConfig(
      devIrlFrontendConfig = devIrlFrontendConfig,
      serverConfig = appServerConfig,
      postgresqlConfig = containerPostgreSqlConfig,
      redisConfig = redisConfig,
      awsS3Config = s3Config,
      stripeConfig = stripeConfig
    )
}
