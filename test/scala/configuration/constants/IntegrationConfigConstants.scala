package configuration.constants

import configuration.models.*

object IntegrationConfigConstants {

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
}
