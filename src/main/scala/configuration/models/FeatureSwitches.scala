package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class FeatureSwitches(
  useDockerHost: Boolean,
  localTesting: Boolean,
  useCors: Boolean,
  useHttpsLocalstack: Boolean,
  useProdStripe: Boolean
) derives ConfigReader
