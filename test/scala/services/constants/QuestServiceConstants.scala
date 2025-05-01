package services.constants

import models.*
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import testData.TestConstants.*

object QuestServiceConstants {

  def testQuestRequest(userId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      userId = userId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

  def testQuest(userId: String, questId: String): QuestPartial =
    QuestPartial(
      userId = userId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

}
