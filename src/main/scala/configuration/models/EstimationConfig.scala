package configuration.models

import cats.kernel.Eq
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

case class EstimationConfig(
  localBucketSeconds: Int,
  localMinimumEstimationWindowSeconds: Int,
  prodBucketSeconds: Int,
  prodMinimumEstimationWindowSeconds: Int,
  intervalMinutes: Int,
  estimationThreshold: Int,
  maxDailyReviews: Int
) derives ConfigReader
