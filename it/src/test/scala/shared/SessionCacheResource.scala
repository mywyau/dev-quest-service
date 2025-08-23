package shared

import infrastructure.cache.SessionCacheAlgebra
import cats.effect.IO

final case class SessionCacheResource(sessionCache: SessionCacheAlgebra[IO])
