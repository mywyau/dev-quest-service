package cache

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.syntax.all.*
import configuration.models.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.auth.UserSession
import models.cache.*
import org.http4s.circe.*
import org.http4s.EntityDecoder
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

trait SessionCacheAlgebra[F[_]] {

  def getSession(userId: String): F[Option[String]]

  def updateSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]]

  def deleteSession(userId: String): F[Long]
}

class SessionCacheImpl[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig) extends SessionCacheAlgebra[F] {

  implicit val updateDecoder: EntityDecoder[F, UserSession] = jsonOf[F, UserSession]

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] = {
    val redisUri = s"redis://$redisHost:$redisPort"
    Logger[F].info(s"[SessionCache] Uri: $redisUri") *>
      Redis[F].utf8(redisUri).use(fa)
  }

  override def getSession(userId: String): F[Option[String]] =
    Logger[F].info(s"[SessionCache] Retrieving session for userId=$userId") *>
      withRedis(_.get(s"auth:session:$userId")).flatTap {
        case Some(_) => Logger[F].info(s"[SessionCache] Session found for userId=$userId")
        case None => Logger[F].info(s"[SessionCache] No session found for userId=$userId")
      }

  // No difference to def storeSession
  override def updateSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    session match {
      case Some(sess) =>
        for {
          _ <- Logger[F].info(s"[SessionCache] Updating session for userId=$userId")
          _ <- withRedis(_.setEx(s"auth:session:$userId", sess.asJson.noSpaces, 1.day))
          _ <- Logger[F].info(s"[SessionCache] Session updated with TTL 1 day for userId=$userId")
        } yield (
          Valid(CacheUpdateSuccess)
        )

      case None =>
        Logger[F].info(s"[SessionCache] No session provided, skipping update for userId=$userId") *>
          Validated.invalidNel(CacheUpdateFailure).pure[F]
    }

  override def deleteSession(userId: String): F[Long] =
    Logger[F].info(s"[SessionCache] Deleting session for userId=$userId") *>
      withRedis(_.del(s"auth:session:$userId")).flatTap { deleted =>
        if (deleted > 0)
          Logger[F].info(s"[SessionCache] Successfully deleted session for userId=$userId")
        else
          Logger[F].info(s"[SessionCache] No session to delete for userId=$userId")
      }
}

object SessionCache {

  import dev.profunktor.redis4cats.effect.Log.Stdout.given // With logs
  // import dev.profunktor.redis4cats.effect.Log.NoOp.given // No logs

  def apply[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig): SessionCacheAlgebra[F] =
    new SessionCacheImpl[F](redisHost, redisPort, appConfig)

  def make[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig): Resource[F, SessionCacheAlgebra[F]] =
    Resource.pure(apply(redisHost, redisPort, appConfig))
}
