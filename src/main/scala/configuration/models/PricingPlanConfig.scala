package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class PricingPlanConfig(
  cacheTtlMinutes: Int,
) derives ConfigReader
