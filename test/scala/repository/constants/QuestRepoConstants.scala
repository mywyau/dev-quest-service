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

  // def createMockRepo(initialUsers: List[QuestPartial]): IO[MockQuestRepository] =
  //   Ref.of[IO, List[QuestPartial]](initialUsers).map(users => MockQuestRepository(users.))

  def testCreateQuestPartial(userId: String): CreateQuestPartial =
    CreateQuestPartial(
      userId = userId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

  def testQuestPartial(userId: String): QuestPartial =
    QuestPartial(
      userId = userId,
      title = "",
      description = Some(""),
      status = Some(InProgress)
    )

}
