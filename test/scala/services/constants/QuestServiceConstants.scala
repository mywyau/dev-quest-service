package services.constants

import models.*
import models.Iron
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import testData.TestConstants.*

object QuestServiceConstants {

  def testQuestRequest(clientId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      rank = Iron,
      title = "",
      description = Some(""),
      acceptanceCriteria = ""
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
      status = Some(InProgress)
    )

}
