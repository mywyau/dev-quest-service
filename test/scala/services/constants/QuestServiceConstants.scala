package services.constants

import models.*
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import testData.TestConstants.*

object QuestServiceConstants {

  def testQuestRequest(clientId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      title = "",
      description = Some("")
    )

  def testQuest(clientId: String, devId: Option[String], questId: String): QuestPartial =
    QuestPartial(
      clientId = clientId,
      devId = devId,
      questId = questId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

}
