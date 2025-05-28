package shared

import cache.RedisCacheAlgebra
import cache.SessionCacheAlgebra
import cats.effect.IO
import configuration.models.AppConfig
import configuration.ConfigReader
import configuration.ConfigReaderAlgebra
import doobie.Transactor
import org.http4s.client.Client
import org.http4s.server.Server

// Define a wrapper case class to help with runtime type issues
final case class TransactorResource(xa: Transactor[IO])

final case class HttpClientResource(client: Client[IO])

final case class RedisCacheResource(redis: RedisCacheAlgebra[IO])

final case class SessionCacheResource(sessionCache: SessionCacheAlgebra[IO])
