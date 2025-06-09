package shared

import cache.RedisCacheAlgebra
import cache.SessionCacheAlgebra
import cats.effect.IO

// Define a wrapper case class to help with runtime type issues

final case class RedisCacheResource(redis: RedisCacheAlgebra[IO])
