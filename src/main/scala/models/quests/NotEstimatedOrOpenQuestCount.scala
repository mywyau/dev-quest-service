package models.quests

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class NotEstimatedOrOpenQuestCount(numberOfQuests:Int)

object NotEstimatedOrOpenQuestCount {
  implicit val encoder: Encoder[NotEstimatedOrOpenQuestCount] = deriveEncoder[NotEstimatedOrOpenQuestCount]
  implicit val decoder: Decoder[NotEstimatedOrOpenQuestCount] = deriveDecoder[NotEstimatedOrOpenQuestCount]
}
