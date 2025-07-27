package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class EstimationConfig(
  countdownDurationMillis: Int,
  estimationThreshold: Int,
  maxDailyReviews: Int
) derives ConfigReader