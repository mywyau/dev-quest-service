package configuration.models

import cats.kernel.Eq
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

case class FeatureSwitches(useDockerHost: Boolean, localTesting: Boolean) derives ConfigReader

case class RedisConfig(dockerHost: String, host: String, port: Int) derives ConfigReader

case class ServerConfig(host: String, port: Int) derives ConfigReader

case class PostgresqlConfig(dbName: String, dockerHost: String, host: String, port: Int, username: String, password: String) derives ConfigReader

case class LocalConfig(serverConfig: ServerConfig, postgresqlConfig: PostgresqlConfig, redisConfig: RedisConfig) derives ConfigReader

case class IntegrationSpecConfig(serverConfig: ServerConfig, postgresqlConfig: PostgresqlConfig, redisConfig: RedisConfig) derives ConfigReader

case class AppConfig(featureSwitches: FeatureSwitches, localConfig: LocalConfig, integrationSpecConfig: IntegrationSpecConfig) derives ConfigReader
