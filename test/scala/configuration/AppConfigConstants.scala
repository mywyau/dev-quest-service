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
      dbName = "shared_test_db",
      dockerHost = "N/A",
      host = "localhost",
      port = 5432,
      username = "shared_user",
      password = "share"
    )

  val containerPostgresqlConfig =
    PostgresqlConfig(
      dbName = "shared_db",
      dockerHost = "shared-postgres-container",
      host = "localhost",
      port = 5432,
      username = "shared_user",
      password = "share"
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
