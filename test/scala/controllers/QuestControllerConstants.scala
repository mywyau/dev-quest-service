package controllers

import java.time.LocalDateTime
import models.quests.QuestPartial
import models.Completed

object QuestControllerConstants {

  val sampleQuest1: QuestPartial =
    QuestPartial(
      clientId = "user_1",
      devId = Some("dev_1"),
      questId = "quest1",
      title = "business1",
      description = Some(""),
      status = Some(Completed)
    )
}
