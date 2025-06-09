package shared

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.server.Server

final case class HttpClientResource(client: Client[IO])
