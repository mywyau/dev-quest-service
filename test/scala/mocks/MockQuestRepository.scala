package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.quests.*
import repositories.QuestRepositoryAlgebra

case class MockQuestRepository(
  existingQuest: Map[String, QuestPartial] = Map.empty
) extends QuestRepositoryAlgebra[IO] {

  override def streamAll(limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  def showAllUsers: IO[Map[String, QuestPartial]] = IO.pure(existingQuest)

  override def streamByUserId(userId: String, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def findAllByUserId(userId: String): IO[List[QuestPartial]] = ???

  override def findByQuestId(businessId: String): IO[Option[QuestPartial]] = IO.pure(existingQuest.get(businessId))

  override def create(request: CreateQuest): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = IO.pure(Valid(CreateSuccess))

  override def update(businessId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def delete(businessId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def deleteAllByUserId(userId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
}
