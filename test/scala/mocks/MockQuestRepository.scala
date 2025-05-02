package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import java.time.LocalDateTime
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import repositories.QuestRepositoryAlgebra
import weaver.SimpleIOSuite

case class MockQuestRepository(
  existingQuest: Map[String, QuestPartial] = Map.empty
) extends QuestRepositoryAlgebra[IO] {

  override def findByUserId(userId: String): IO[Option[QuestPartial]] = ???

  def showAllUsers: IO[Map[String, QuestPartial]] = IO.pure(existingQuest)

  override def findByQuestId(businessId: String): IO[Option[QuestPartial]] = IO.pure(existingQuest.get(businessId))

  override def create(request: CreateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = IO.pure(Valid(CreateSuccess))

  override def update(businessId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def delete(businessId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def deleteAllByUserId(userId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
}
