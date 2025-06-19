package repository.constants

import cats.data.Validated.Valid
import cats.effect.kernel.Ref
import cats.effect.IO
import java.time.LocalDateTime
import mocks.MockQuestRepository
import models.languages.Python
import models.languages.Scala
import models.languages.TypeScript
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.InProgress
import models.Iron
import repositories.QuestRepositoryAlgebra

object QuestRepoConstants {

  def testCreateQuestPartial(clientId: String, questId: String): CreateQuestPartial =
    CreateQuestPartial(
      rank = Iron,
      title = "",
      description = Some(""),
      acceptanceCriteria = "",
      tags = Seq(Python, Scala, TypeScript)
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
      status = Some(InProgress),
      tags = Seq("Python", "Scala", "TypeScript")
    )

}
