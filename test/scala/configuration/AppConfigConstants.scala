package configuration

import configuration.models.*

object AppConfigConstants {

  val appServerConfig =
    ServerConfig(
      host = "0.0.0.0",
      port = 8080
    )

  val integrationSpecServerConfig =
    ServerConfig(
      host = "127.0.0.1",
      port = 9999
    )

  val integrationPostgresqlConfig =
    PostgresqlConfig(
      dbName = "dev_quest_test_db",
      dockerHost = "dev-quest-db-it",
      host = "localhost",
      port = 5431,
      username = "dev_quest_test_user",
      password = "turnip"
    )

  val containerPostgresqlConfig =
    PostgresqlConfig(
      dbName = "dev_quest",
      dockerHost = "dev-quest-container",
      host = "localhost",
      port = 5432,
      username = "dev_quest_user",
      password = "turnip"
    )

  val integrationSpecConfig =
    IntegrationSpecConfig(
      serverConfig = integrationSpecServerConfig,
      postgresqlConfig = integrationPostgresqlConfig
    )

  val localConfig =
    LocalConfig(
      serverConfig = appServerConfig,
      postgresqlConfig = containerPostgresqlConfig
    )

  val featureSwitches =
    FeatureSwitches(
      useDockerHost = false
    )

  val appConfig =
    AppConfig(
      featureSwitches = featureSwitches,
      localConfig = localConfig,
      integrationSpecConfig = integrationSpecConfig
    )

}
