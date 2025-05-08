package configuration

import configuration.models.*

object AppConfigConstants {

  val featureSwitches =
    FeatureSwitches(
      useDockerHost = false
    )

  val appServerConfig =
    ServerConfig(
      host = "0.0.0.0",
      port = 8080
    )

  val containerPostgresqlConfig =
    PostgresqlConfig(
      dbName = "dev_quest_db",
      dockerHost = "dev-quest-container",
      host = "localhost",
      port = 5432,
      username = "dev_quest_user",
      password = "turnip"
    )

  val localConfig =
    LocalConfig(
      serverConfig = appServerConfig,
      postgresqlConfig = containerPostgresqlConfig
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

  val integrationSpecConfig =
    IntegrationSpecConfig(
      serverConfig = integrationSpecServerConfig,
      postgresqlConfig = integrationPostgresqlConfig
    )

  val appConfig =
    AppConfig(
      featureSwitches = featureSwitches,
      localConfig = localConfig,
      integrationSpecConfig = integrationSpecConfig
    )

}
