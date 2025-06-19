package controllers

import cache.RedisCacheAlgebra
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.effect.IO
import cats.implicits.*
import fs2.Stream
import models.auth.UserSession
import models.database.*
import models.database.CreateSuccess
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.database.DeleteSuccess
import models.database.UpdateSuccess
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import services.QuestServiceAlgebra
import cats.effect.IO
import cats.effect.Ref
import cache.RedisCacheAlgebra
import models.auth.UserSession
import models.QuestStatus
import models.Rank

class MockQuestService(userQuestData: Map[String, QuestPartial]) extends QuestServiceAlgebra[IO] {

  override def completeQuestAwardXp(questId: String, questStatus: QuestStatus, rank: Rank): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def streamClient(clientId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamDev(devId: String, questStatus: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def updateStatus(questId: String, questStatus: QuestStatus): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def acceptQuest(questId: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def streamAll(limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def streamByUserId(userId: String, limit: Int, offset: Int): Stream[IO, QuestPartial] = ???

  override def getAllQuests(userId: String): IO[List[QuestPartial]] = ???

  override def getByQuestId(businessId: String): IO[Option[QuestPartial]] =
    userQuestData.get(businessId) match {
      case Some(address) => IO.pure(Some(address))
      case None => IO.pure(None)
    }

  override def create(request: CreateQuestPartial, userId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(CreateSuccess))

  override def update(businessId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(UpdateSuccess))

  override def delete(businessId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(DeleteSuccess))
}

/** 
 * A fully‐customizable mock.  
 * 
 * @param ref
 *   The in‐memory map Ref.  
 * @param mkSession
 *   A function the mock will use to turn (userId, token) → UserSession  
 */

class MockRedisCache(
  ref: Ref[IO, Map[String, UserSession]],
  mkSession: (String, String) => UserSession = (userId, token) =>
    UserSession(
      userId      = userId,
      cookieValue = token,
      email       = s"$userId@example.com",
      userType    = "Dev"
    )
) extends RedisCacheAlgebra[IO] {

  private def keyFor(userId: String): String =
    s"auth:session:$userId"

  override def storeSession(userId: String, token: String): IO[Unit] =
    // use mkSession to build a customized UserSession
    ref.update { m =>
      m.updated(keyFor(userId), mkSession(userId, token))
    }

  override def getSession(userId: String): IO[Option[UserSession]] =
    ref.get.map(_.get(keyFor(userId)))

  // For a mock, update is just a re‐store
  override def updateSession(userId: String, token: String): IO[Unit] =
    storeSession(userId, token)

  override def deleteSession(userId: String): IO[Long] =
    ref.modify { m =>
      val k = keyFor(userId)
      if (m.contains(k)) (m - k, 1L)
      else            (m,   0L)
    }
}
