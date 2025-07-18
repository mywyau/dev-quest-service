package controllers

import models.Completed
import models.Iron
import models.quests.QuestPartial

import java.time.LocalDateTime

object QuestControllerConstants {

  val sampleQuest1: QuestPartial =
    QuestPartial(
      clientId = "client123",
      devId = Some("dev123"),
      questId = "quest1",
      rank = Iron,
      title = "business1",
      description = Some("some description"),
      acceptanceCriteria = Some("some acceptance criteria"),
      status = Some(Completed),
      tags = Seq("Python", "Scala", "TypeScript"),
      estimated = true
    )
}
