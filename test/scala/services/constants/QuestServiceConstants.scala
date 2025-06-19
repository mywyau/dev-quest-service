package services.constants

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
      title = "",
      description = Some(""),
      acceptanceCriteria = "",
      tags = Seq(Python, Scala, TypeScript)
    )

  def testQuest(clientId: String, devId: Option[String], questId: String): QuestPartial =
    QuestPartial(
      clientId = clientId,
      devId = devId,
      questId = questId,
      rank = Iron,
      title = "",
      description = Some(""),
      acceptanceCriteria = Some(""),
      status = Some(InProgress),
      tags = Seq("Python", "Scala", "TypeScript")
    )

}
