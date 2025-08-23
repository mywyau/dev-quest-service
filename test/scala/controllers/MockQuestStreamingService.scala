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
import models.quests.*
import models.QuestStatus
import models.Rank
import services.QuestStreamingServiceAlgebra

class MockQuestStreamingService(userQuestData: Map[String, QuestPartial]) extends QuestStreamingServiceAlgebra[IO] {

  override def streamClient(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamAll(limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamAllWithRewards(limit: Int, offset: Int): Stream[IO, QuestWithReward] = ???

  override def streamByUserId(userId: String, limit: Int, offset: Int): Stream[IO, QuestWithReward] = ???
}
