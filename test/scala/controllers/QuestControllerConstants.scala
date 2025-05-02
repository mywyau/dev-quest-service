package controllers

import java.time.LocalDateTime
import models.quests.QuestPartial
import models.Completed

object QuestControllerConstants {

  val sampleQuest1: QuestPartial =
    QuestPartial(
      userId = "user_1",
      questId = "quest1",
      title = "business1",
      description = Some(""),
      status = Some(Completed)
    )
}
