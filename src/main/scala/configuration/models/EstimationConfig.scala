package configuration.models

import cats.kernel.Eq
import pureconfig.ConfigReader
import pureconfig.generic.derivation.*

case class EstimationConfig(
  localBucketSeconds: Int,
  localMinimumEstimationWindowSeconds: Int,
  prodBucketSeconds: Int,
  prodMinimumEstimationWindowSeconds: Int,
  intervalSeconds: Int,
  estimationThreshold: Int,
  maxDailyEstimates: Int
) derives ConfigReader
