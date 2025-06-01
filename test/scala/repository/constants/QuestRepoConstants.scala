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

object QuestRepoConstants {

  def testCreateQuestPartial(clientId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      title = "",
      description = Some("")
    )

  def testQuestPartial(clientId: String, devId: Option[String], questId: String): QuestPartial =
    QuestPartial(
      clientId = clientId,
      devId = devId,
      questId = questId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

}
