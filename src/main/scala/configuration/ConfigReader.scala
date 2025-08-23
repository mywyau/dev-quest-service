package configuration

import cats.effect.Sync
import configuration.models.ServicesConfig
import pureconfig.ConfigSource

trait EnvProvider { def current: String } // returns "local" | "prod" | "integration"

object EnvProvider {

  val system: EnvProvider = new EnvProvider {
    def current: String =
      sys.props.getOrElse("app.env", sys.env.getOrElse("APP_ENV", "local")).toLowerCase()
  }
}

trait ConfigReaderAlgebra[F[_]] {

  def loadAppConfig: F[AppConfig]
}

class ConfigReaderImpl[F[_] : Sync](env: EnvProvider) extends ConfigReaderAlgebra[F] {
  override def loadAppConfig: F[AppConfig] =
    Sync[F].delay {
      val e = env.current

      val source = e match {
        case "prod" =>
          ConfigSource
            .resources("application.prod.conf")
            .withFallback(ConfigSource.default)

        case "local" =>
          ConfigSource
            .resources("application.local.conf")
            .withFallback(ConfigSource.default)

        case "integration" =>
          ConfigSource
            .resources("application.integration.conf")
            .withFallback(ConfigSource.default)

        case other =>
          throw new Exception(s"Unsupported env '$other'")
      }

      source.loadOrThrow[AppConfig]
    }
}

object ConfigReader {
  def apply[F[_] : Sync]: ConfigReaderAlgebra[F] =
    new ConfigReaderImpl[F](EnvProvider.system)
}

// class ConfigReaderImpl[F[_] : Sync] extends ConfigReaderAlgebra[F] {

//   override def loadAppConfig: F[AppConfig] =
//     Sync[F].delay {

//       val env = sys.env.getOrElse("APP_ENV", "local").toLowerCase()

//       val configSource = env match {
//         case "prod" =>
//           ConfigSource.default
//             .withFallback(ConfigSource.resources("application.prod.conf"))
//         case "local" =>
//           ConfigSource.default
//             .withFallback(ConfigSource.resources("application.local.conf"))
//         case other =>
//           throw new Exception(s"Unsupported APP_ENV: $other (expected 'local' or 'prod')")
//       }

//       configSource.loadOrThrow[AppConfig]
//     }
// }

// object ConfigReader {
//   def apply[F[_] : Sync]: ConfigReaderAlgebra[F] =
//     new ConfigReaderImpl[F]
// }
