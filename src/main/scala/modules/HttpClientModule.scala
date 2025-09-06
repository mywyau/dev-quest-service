package modules

import cats.effect.*
import org.http4s.client.*
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

object HttpClientModule {

  def make[F[_]: Async: Logger]: Resource[F, Client[F]] =
    EmberClientBuilder.default[F].build
}