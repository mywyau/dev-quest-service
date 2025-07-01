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
import models.quests.* 
import services.QuestServiceAlgebra
import cats.effect.IO
import cats.effect.Ref
import cache.RedisCacheAlgebra
import models.auth.UserSession
import models.QuestStatus
import models.Rank


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