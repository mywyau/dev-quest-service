package repository.constants

import cats.data.Validated.Valid
import cats.effect.kernel.Ref
import cats.effect.IO
import java.time.LocalDateTime
import mocks.MockQuestRepository
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.InProgress
import repositories.QuestRepositoryAlgebra
import models.Iron

object QuestRepoConstants {

  def testCreateQuestPartial(clientId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      rank = Iron,
      title = "",
      description = Some(""),
      acceptanceCriteria = ""
    )

  def testQuestPartial(clientId: String, devId: Option[String], questId: String): QuestPartial =
    QuestPartial(
      clientId = clientId,
      devId = devId,
      questId = questId,
      title = "",
      rank = Iron,
      description = Some(""),
      acceptanceCriteria = Some(""),
      status = Some(InProgress)
    )

}
