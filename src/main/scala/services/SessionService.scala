package services

import cache.SessionCacheAlgebra
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import fs2.Stream
import java.util.UUID
import models.auth.UserSession
import models.cache.*
import models.users.*
import models.Dev
import models.UnknownUserType
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.UserDataRepositoryAlgebra

trait SessionServiceAlgebra[F[_]] {

  def getSession(userId: String): F[Option[String]]

  def storeOnlyCookie(userId: String, token: String): F[Unit]

  def storeUserSession(userId: String, cookieToken: String): F[ValidatedNel[CacheErrors, CacheSuccess]]

  def updateUserSession(userId: String, cookieToken: String): F[ValidatedNel[CacheErrors, CacheSuccess]]

  def deleteSession(userId: String): F[Long]
}

class SessionServiceImpl[F[_] : Concurrent : Monad : Logger](
  userRepo: UserDataRepositoryAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends SessionServiceAlgebra[F] {

  override def getSession(userId: String): F[Option[String]] =
    sessionCache.getSession(userId)

  override def storeOnlyCookie(userId: String, cookieToken: String): F[Unit] = 
    sessionCache.storeOnlyCookie(userId, cookieToken)

  override def storeUserSession(
    userId: String,
    cookieToken: String
  ): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    userRepo.findUser(userId).flatMap {
      case Some(userDetails) =>
        val userSession =
          UserSession(
            userId = userDetails.userId,
            cookieToken = cookieToken,
            email = userDetails.email,
            userType = userDetails.userType.getOrElse(UnknownUserType).toString()
          )
        sessionCache.storeSession(userId, Some(userSession))
      case None =>
        Validated
          .invalidNel[CacheErrors, CacheSuccess](CacheUpdateFailure)
          .pure[F]
    }

  override def updateUserSession(
    userId: String,
    cookieToken: String
  ): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    userRepo.findUser(userId).flatMap {
      case Some(userDetails) =>
        val userSession =
          UserSession(
            userId = userDetails.userId,
            cookieToken = cookieToken,
            email = userDetails.email,
            userType = userDetails.userType.getOrElse(UnknownUserType).toString()
          )
        sessionCache.storeSession(userId, Some(userSession))
      case None =>
        Validated
          .invalidNel[CacheErrors, CacheSuccess](CacheUpdateFailure)
          .pure[F]
    }

  override def deleteSession(userId: String): F[Long] =
    sessionCache.deleteSession(userId)

}

object SessionService {

  def apply[F[_] : Concurrent : Logger](
    userRepo: UserDataRepositoryAlgebra[F],
    sessionCache: SessionCacheAlgebra[F]
  ): SessionServiceAlgebra[F] =
    new SessionServiceImpl[F](userRepo, sessionCache)
}
