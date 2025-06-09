package shared

import cats.effect.IO
import doobie.Transactor

// Define a wrapper case class to help with runtime type issues
final case class TransactorResource(xa: Transactor[IO])
