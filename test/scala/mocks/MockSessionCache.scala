package mocks

import cats.effect.{Ref, Sync}
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import models.auth.UserSession
import models.cache.{CacheErrors, CacheUpdateFailure, CacheUpdateSuccess, CacheSuccess}
import cache.SessionCacheAlgebra

class MockSessionCache[F[_]: Sync] private (
  sessions: Ref[F, Map[String, UserSession]],
  cookies:  Ref[F, Map[String, String]]
) extends SessionCacheAlgebra[F] {

  // just look up the raw token
  override def getSessionCookieOnly(userId: String): F[Option[String]] =
    cookies.get.map(_.get(userId))

  // look up the parsed UserSession
  override def getSession(userId: String): F[Option[UserSession]] =
    sessions.get.map(_.get(userId))

  // store only a token
  override def storeOnlyCookie(userId: String, token: String): F[Unit] =
    cookies.update(_ + (userId -> token))

  // store a full session; fails if None
  override def storeSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    session match {
      case Some(sess) =>
        sessions.update(_ + (userId -> sess)) *> 
          Sync[F].pure(Valid(CacheUpdateSuccess))
      case None =>
        Sync[F].pure(Invalid(CacheUpdateFailure).toValidatedNel)
    }

  // identical to storeSession in this mock
  override def updateSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    storeSession(userId, session)

  // delete both cookie+session; return 1 if anything was actually removed
  override def deleteSession(userId: String): F[Long] =
    for {
      sessGone <- sessions.modify { m =>
                    val had = m.contains(userId)
                    (m - userId, if (had) 1L else 0L)
                  }
      cookGone <- cookies.modify { m =>
                    val had = m.contains(userId)
                    (m - userId, if (had) 1L else 0L)
                  }
    } yield ((sessGone + cookGone) min 1)  // mimic Redis `DEL` on a single key

  // find a UserSession by token
  override def lookupSession(token: String): F[Option[UserSession]] =
    (cookies.get, sessions.get).mapN { (cookMap, sessMap) =>
      cookMap.collectFirst { case (uid, t) if t == token => sessMap.get(uid) }.flatten
    }
}

object MockSessionCache {
  /** Build a brand-new in-memory cache for F (e.g. IO) */
  def make[F[_]: Sync]: F[SessionCacheAlgebra[F]] =
    for {
      sessionsRef <- Ref.of[F, Map[String, UserSession]](Map.empty)
      cookiesRef  <- Ref.of[F, Map[String, String]](Map.empty)
    } yield new MockSessionCache[F](sessionsRef, cookiesRef)
}
