package configuration

import cats.effect.Sync
import configuration.models.ServicesConfig
import pureconfig.ConfigSource

trait ConfigReaderAlgebra[F[_]] {

  def loadAppConfig: F[AppConfig]
}

class ConfigReaderImpl[F[_] : Sync] extends ConfigReaderAlgebra[F] {

  override def loadAppConfig: F[AppConfig] =
    Sync[F].delay {
      ConfigSource.resources("application.conf").loadOrThrow[AppConfig]
    }
}

object ConfigReader {
  def apply[F[_] : Sync]: ConfigReaderAlgebra[F] =
    new ConfigReaderImpl[F]
}
