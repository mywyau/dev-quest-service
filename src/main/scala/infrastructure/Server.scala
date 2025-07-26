package infrastructure

import cats.effect.Async
import cats.effect.Resource
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger as Http4sLogger
import org.http4s.HttpRoutes

object Server {

  def create[F[_] : Async](
    host: Host,
    port: Port,
    routes: HttpRoutes[F]
  ): Resource[F, Unit] = {
    val httpApp = Http4sLogger.httpApp(logHeaders = true, logBody = true)(routes.orNotFound)

    EmberServerBuilder
      .default[F]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .void
  }

}
