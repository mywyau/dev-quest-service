package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class DevSubmissionConfig(
  expiryDays: Int
)
