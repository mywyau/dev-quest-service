package configuration.constants

import configuration.models.*

object LocalAppConfigConstants {

  val devIrlFrontendConfig =
    DevIrlFrontendConfig(
      host = "0.0.0.0",
      port = 3000,
      baseUrl = "http://localhost:3000"
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

  val localAppConfig =
    LocalAppConfig(
      devIrlFrontendConfig = devIrlFrontendConfig,
      serverConfig = appServerConfig,
      postgresqlConfig = containerPostgreSqlConfig,
      redisConfig = redisConfig,
      awsS3Config = s3Config,
      stripeConfig = stripeConfig
    )

}
