package infrastructure

import cats.effect.{Async, Resource}
import configuration.AppConfig

object Redis {

  def address[F[_]: Async](appConfig: AppConfig): Resource[F, (String, Int)] = {
    val redisHost = sys.env.getOrElse("REDIS_HOST", appConfig.redisConfig.host)
    val redisPort = sys.env.get("REDIS_PORT").flatMap(_.toIntOption).getOrElse(appConfig.redisConfig.port)

    Resource.eval(Async[F].pure((redisHost, redisPort)))
  }
}
