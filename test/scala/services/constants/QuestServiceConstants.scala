package services.constants

import java.time.Instant
import java.time.LocalTime
import models.*
import models.database.*
import models.languages.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.Iron
import testData.TestConstants.*

object QuestServiceConstants {

  def testQuestRequest(clientId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      rank = Iron,
      title = "a quest title",
      description = Some("some description"),
      acceptanceCriteria = "some acceptance criteria",
      tags = Seq(Python, Scala, TypeScript)
    )

  def testQuest(clientId: String, devId: Option[String], questId: String): QuestPartial = {

    QuestPartial(
      clientId = clientId,
      devId = devId,
      questId = questId,
      rank = Iron,
      title = "a quest title",
      description = Some("some description"),
      acceptanceCriteria = Some("some acceptance criteria"),
      status = Some(InProgress),
      tags = Seq("Python", "Scala", "TypeScript"),
      estimated = true
    )
  }
}
