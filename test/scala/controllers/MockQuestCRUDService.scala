package controllers

import infrastructure.cache.*
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.effect.IO
import cats.effect.Ref
import cats.implicits.*
import fs2.Stream
import models.auth.UserSession
import models.database.*
import models.kafka.Failure
import models.kafka.KafkaProducerResult
import models.kafka.SuccessfulWrite
import models.quests.*
import models.work_time.HoursOfWork
import models.QuestStatus
import models.Rank
import services.QuestCRUDServiceAlgebra

class MockQuestCRUDService(userQuestData: Map[String, QuestPartial]) extends QuestCRUDServiceAlgebra[IO] {

  override def getHoursOfWork(questId: String): IO[Option[HoursOfWork]] = ???

  override def upsertHoursOfWork(clientId: String, questId: String, request: HoursOfWork): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def countNotEstimatedAndOpenQuests(): IO[Int] = ???

  override def completeQuestAwardXp(questId: String, questStatus: QuestStatus, rank: Rank): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def updateStatus(questId: String, questStatus: QuestStatus): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def acceptQuest(questId: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def getByQuestId(businessId: String): IO[Option[QuestPartial]] =
    userQuestData.get(businessId) match {
      case Some(address) => IO.pure(Some(address))
      case None => IO.pure(None)
    }
  override def create(request: CreateQuestPartial, clientId: String): IO[ValidatedNel[Failure, KafkaProducerResult]] =
    IO.pure(Valid(SuccessfulWrite))

  override def update(businessId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(UpdateSuccess))

  override def delete(businessId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(DeleteSuccess))
}
