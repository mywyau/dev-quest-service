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

  def testCreateQuestPartial(userId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      title = "",
      description = Some("")
    )

  def testQuestPartial(userId: String, questId: String): QuestPartial =
    QuestPartial(
      userId = userId,
      questId = questId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

}
