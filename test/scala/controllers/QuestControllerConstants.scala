package controllers

import java.time.Instant
import java.time.LocalDateTime
import models.quests.QuestPartial
import models.Completed
import models.Iron
import testData.TestConstants.*

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
      estimationCloseAt = Some(fixed_instant_2025_07_1200),
      estimated = true
    )
}
