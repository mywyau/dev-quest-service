package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class StripeConfig(
  registrationRefreshUrl: String,
  registrationReturnUrl: String,
  paymentSuccessUrl: String,
  paymentCancelUrl: String,
  stripeUrl: String,
  platformFeePercent: BigDecimal
) derives ConfigReader