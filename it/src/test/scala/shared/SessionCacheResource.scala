package shared

import cache.SessionCacheAlgebra
import cats.effect.IO

final case class SessionCacheResource(sessionCache: SessionCacheAlgebra[IO])
