package shared

import cats.effect.IO
import infrastructure.cache.*

// Define a wrapper case class to help with runtime type issues

final case class RedisCacheResource(redis: RedisCacheAlgebra[IO])
