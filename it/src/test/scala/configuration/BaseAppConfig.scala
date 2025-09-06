package configuration

import cats.effect.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import configuration.models.*
import configuration.AppConfig

trait BaseAppConfig {

  val configReader: ConfigReaderAlgebra[IO] = ConfigReader[IO]

  def appConfigResource: Resource[IO, AppConfig] =
    Resource.eval(
      configReader.loadAppConfig
        .handleErrorWith { e =>
          IO.raiseError(new RuntimeException(s"[ControllerSharedResource] Failed to load app configuration: ${e.getMessage}", e))
        }
    )

  def hostResource(appConfig: AppConfig): Resource[IO, Host] =
    Resource.eval(
      IO.fromEither(
        Host
          .fromString(appConfig.serverConfig.host)
          .toRight(new RuntimeException("[ControllerSharedResource] Invalid host configuration"))
      )
    )

  def portResource(appConfig: AppConfig): Resource[IO, Port] =
    Resource.eval(
      IO.fromEither(
        Port
          .fromInt(appConfig.serverConfig.port)
          .toRight(new RuntimeException("[ControllerSharedResource] Invalid port configuration"))
      )
    )

  def postgresqlConfigResource(appConfig: AppConfig): Resource[IO, PostgresqlConfig] =
    Resource.eval(
      IO(appConfig.postgresqlConfig)
    )

  def redisConfigResource(appConfig: AppConfig): Resource[IO, RedisConfig] =
    Resource.eval(
      IO(appConfig.redisConfig)
    )
}
