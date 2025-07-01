package configuration.models

import cats.kernel.Eq
import pureconfig.generic.derivation.*
import pureconfig.ConfigReader

case class QuestConfig(
  maxActiveQuests: Int,
  bronzeXp: Double, 
  ironXp: Double, 
  steelXp: Double, 
  mithrilXp: Double, 
  adamantiteXp: Double, 
  runicXp: Double, 
  demonXp: Double, 
  ruinousXp: Double, 
  aetherXp: Double, 
) derives ConfigReader