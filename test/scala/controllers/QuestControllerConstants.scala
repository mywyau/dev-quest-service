package controllers

import java.time.LocalDateTime
import models.quests.QuestPartial
import models.Completed
import models.Iron

object QuestControllerConstants {

  val sampleQuest1: QuestPartial =
    QuestPartial(
      clientId = "user_1",
      devId = Some("dev_1"),
      questId = "quest1",
      rank = Iron,
      title = "business1",
      description = Some(""),
      acceptanceCriteria = Some(""),
      status = Some(Completed),
      tags = Seq("Python", "Scala", "TypeScript")
    )
}
