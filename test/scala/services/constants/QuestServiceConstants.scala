package services.constants

import models.*
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import testData.TestConstants.*

object QuestServiceConstants {

  def testQuestRequest(userId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      title = "",
      description = Some("")
    )

  def testQuest(userId: String, questId: String): QuestPartial =
    QuestPartial(
      userId = userId,
      questId = questId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

}
