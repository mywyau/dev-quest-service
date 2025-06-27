package cache

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.syntax.all.*
import configuration.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import io.circe.generic.auto._
import io.circe.parser
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.auth.UserSession
import models.cache.*
import org.http4s.circe.*
import org.http4s.EntityDecoder
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

trait SessionCacheAlgebra[F[_]] {

  def getSessionCookieOnly(userId: String): F[Option[String]]

  def getSession(userId: String): F[Option[UserSession]]

  def storeOnlyCookie(userId: String, token: String): F[Unit]

  def storeSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]]

  def updateSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]]

  def deleteSession(userId: String): F[Long]

  // New: lookup by session token
  def lookupSession(token: String): F[Option[UserSession]]
}

class SessionCacheImpl[F[_] : Async : Logger](redisHost: String, redisPort: Int, appConfig: AppConfig) extends SessionCacheAlgebra[F] {

  implicit val userSessionDecoder: EntityDecoder[F, UserSession] = jsonOf[F, UserSession]

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] = {
    val redisUri = s"redis://$redisHost:$redisPort"
    Logger[F].debug(s"[SessionCache] Uri: $redisUri") *>
      Redis[F].utf8(redisUri).use(fa)
  }

  override def getSessionCookieOnly(userId: String): F[Option[String]] =
    Logger[F].debug(s"[SessionCache] Retrieving session for userId=$userId") *>
      withRedis(_.get(s"auth:session:$userId")).flatTap {
        case Some(_) => Logger[F].debug(s"[SessionCache] Session found for userId=$userId")
        case None => Logger[F].debug(s"[SessionCache] No session found for userId=$userId")
      }

  def getSession(userId: String): F[Option[UserSession]] = {

    val key = s"auth:session:$userId"

    for {
      _ <- Logger[F].debug(s"[SessionCache] Retrieving session for userId=$userId")
      maybeJ <- withRedis(_.get(key))
      result <- maybeJ match {
        case None =>
          Logger[F]
            .info(s"[SessionCache][getSession] No session found for userId=$userId")
            .as(None)
        case Some(jsonStr) =>
          Logger[F].debug(s"[SessionCache][getSession] Session JSON for userId=$userId: $jsonStr") *>
            (
              decode[UserSession](jsonStr) match {
                case Right(session) =>
                  Logger[F]
                    .info(s"[SessionCache][getSession] Parsed session for userId=$userId")
                    .as(Some(session))
                case Left(err) =>
                  Logger[F]
                    .error(s"[SessionCache][getSession] JSON parsing failed: $err")
                    .as(None)
              }
            )
      }
    } yield result
  }

  override def storeOnlyCookie(userId: String, token: String): F[Unit] =
    Logger[F].debug(s"[SessionCache] Storing session for userId=$userId") *>
      withRedis(_.setEx(s"auth:session:$userId", token, 1.day)) <*
      Logger[F].debug(s"[SessionCache] Session stored with TTL 1 day for userId=$userId")

  override def storeSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    session match {
      case Some(sess) =>
        for {
          _ <- Logger[F].debug(s"[SessionCache] Storing session for userId=$userId")
          _ <- withRedis(_.setEx(s"auth:session:$userId", sess.asJson.noSpaces, 1.day))
          _ <- Logger[F].debug(s"[SessionCache] Session updated with TTL 1 day for userId=$userId")
        } yield (
          Valid(CacheUpdateSuccess)
        )

      case None =>
        Logger[F].debug(s"[SessionCache] No session provided, skipping update for userId=$userId") *>
          Validated.invalidNel(CacheUpdateFailure).pure[F]
    }

  // No difference to def storeSession
  override def updateSession(userId: String, session: Option[UserSession]): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    session match {
      case Some(sess) =>
        for {
          _ <- Logger[F].debug(s"[SessionCache] Updating session for userId=$userId")
          _ <- withRedis(_.setEx(s"auth:session:$userId", sess.asJson.noSpaces, 1.day))
          _ <- Logger[F].debug(s"[SessionCache] Session updated with TTL 1 day for userId=$userId")
        } yield (
          Valid(CacheUpdateSuccess)
        )

      case None =>
        Logger[F].debug(s"[SessionCache] No session provided, skipping update for userId=$userId") *>
          Validated.invalidNel(CacheUpdateFailure).pure[F]
    }

  override def deleteSession(userId: String): F[Long] =
    Logger[F].debug(s"[SessionCache] Deleting session for userId=$userId") *>
      withRedis(_.del(s"auth:session:$userId")).flatTap { deleted =>
        if (deleted > 0)
          Logger[F].debug(s"[SessionCache] Successfully deleted session for userId=$userId")
        else
          Logger[F].debug(s"[SessionCache] No session to delete for userId=$userId")
      }

    // Implementation of lookupSession
  override def lookupSession(userId: String): F[Option[UserSession]] =
    Logger[F].debug(s"[SessionCache] Looking up session for userId=$userId") *>
      withRedis(_.get(s"auth:session:$userId")).flatMap {
        case Some(json) =>
          parser.decode[UserSession](json) match {
            case Right(sess) =>
              Logger[F].debug(s"[SessionCache] Session found for token=$userId") *> sess.some.pure[F]
            case Left(err) =>
              Logger[F].error(err)(s"[SessionCache] Failed to decode session JSON for token=$userId") *> none[UserSession].pure[F]
          }
        case None =>
          Logger[F].debug(s"[SessionCache] No session found for token=$userId") *> none[UserSession].pure[F]
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
