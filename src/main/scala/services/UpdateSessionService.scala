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
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.UserDataRepositoryAlgebra

trait UpdateSessionServiceAlgebra[F[_]] {

  def updateUserSession(userId: String, cookieToken:String): F[ValidatedNel[CacheErrors, CacheSuccess]]
}

class UpdateSessionServiceImpl[F[_] : Concurrent : Monad : Logger](
  userRepo: UserDataRepositoryAlgebra[F],
  sessionCache: SessionCacheAlgebra[F]
) extends UpdateSessionServiceAlgebra[F] {

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
            userType = userDetails.userType.getOrElse(Dev).toString()
          )
        sessionCache.updateSession(userId, Some(userSession))
      case None =>
        Validated
          .invalidNel[CacheErrors, CacheSuccess](CacheUpdateFailure)
          .pure[F]
    }

}

object UpdateSessionService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    userRepo: UserDataRepositoryAlgebra[F],
    sessionCache: SessionCacheAlgebra[F]
  ): UpdateSessionServiceAlgebra[F] =
    new UpdateSessionServiceImpl[F](userRepo, sessionCache)
}
