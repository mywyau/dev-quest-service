package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.QuestStatus
import models.Rank
import models.database.*
import models.quests.*
import models.work_time.HoursOfWork
import repositories.QuestRepositoryAlgebra

import java.time.Instant

case class MockQuestRepository(
  countActiveQuests: Int = 5,
  existingQuest: Map[String, QuestPartial] = Map.empty
) extends QuestRepositoryAlgebra[IO] {

  override def createHoursOfWork(clientId: String, questId: String, request: HoursOfWork): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  def showAllUsers: IO[Map[String, QuestPartial]] = IO.pure(existingQuest)

  override def countNotEstimatedAndOpenQuests(): IO[Int] = ???

  override def findNotEstimatedQuests(): IO[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]] = ???

  override def setFinalRank(questId: String, rank: Rank): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def countActiveQuests(devId: String): IO[Int] = IO(countActiveQuests)

  override def validateOwnership(questId: String, clientId: String): IO[Unit] = ???

  override def markPaid(questId: String): IO[Unit] = ???

  override def streamByQuestStatusDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def updateStatus(questId: String, questStatus: QuestStatus): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = IO.pure(Valid(UpdateSuccess))

  override def acceptQuest(questId: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = IO(Valid(UpdateSuccess))

  override def streamByQuestStatus(userId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamAll(limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamByUserId(userId: String, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def findAllByUserId(userId: String): IO[List[QuestPartial]] = ???

  override def findByQuestId(questId: String): IO[Option[QuestPartial]] = IO.pure(existingQuest.get(questId))

  override def create(request: CreateQuest): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = IO.pure(Valid(CreateSuccess))

  override def update(questId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = IO.pure(Valid(UpdateSuccess))

  override def delete(questId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def deleteAllByUserId(userId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
}
