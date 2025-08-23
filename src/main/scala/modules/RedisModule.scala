package modules

import cats.effect.*
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.connection.*
import dev.profunktor.redis4cats.effect.Log.Stdout.given
import configuration.AppConfig
// import dev.profunktor.redis4cats.codecs.Codec.Utf8
// import dev.profunktor.redis4cats.codecs.Codec.Utf8
import dev.profunktor.redis4cats.codecs.Codecs
import org.typelevel.log4cats.Logger

object RedisModule {

  def make[F[_]: Async: Logger](cfg: AppConfig): Resource[F, RedisCommands[F, String, String]] = {
    val redisHost = sys.env.getOrElse("REDIS_HOST", cfg.redisConfig.host)
    val redisPort = cfg.redisConfig.port

    val redisUri = s"redis://$redisHost:$redisPort"

    for {
      client <- RedisClient[F].from(redisUri)
      redis  <- Redis[F].utf8(redisUri)
    } yield redis
  }
}
