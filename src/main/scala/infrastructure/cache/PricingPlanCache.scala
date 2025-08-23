package infrastructure.cache

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.effect.*
import cats.syntax.all.*
import configuration.AppConfig
import dev.profunktor.redis4cats.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import io.circe.parser.decode
import io.circe.syntax.*
import models.cache.*            // your CacheErrors/CacheSuccess ADTs
import models.pricing.*          // PlanSnapshot lives here
// import models.pricing_plan.*          // PlanSnapshot lives here
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

trait PricingPlanCacheAlgebra[F[_]] {

  /** Raw JSON read (legacy name kept to avoid breaking callers) */
  def getPricingPlanCookieOnly(userId: String): F[Option[String]]

  /** Get cached PlanSnapshot for a user */
  def getPricingPlan(userId: String): F[Option[PlanSnapshot]]

  /** Store raw JSON (legacy helper; prefer storePricingPlan) */
  def storeOnlyCookie(userId: String, json: String): F[Unit]

  /** Write a PlanSnapshot with TTL */
  def storePricingPlan(userId: String, snapshot: Option[PlanSnapshot]): F[ValidatedNel[CacheErrors, CacheSuccess]]

  /** Update behaves same as store (idempotent) */
  def updatePricingPlan(userId: String, snapshot: Option[PlanSnapshot]): F[ValidatedNel[CacheErrors, CacheSuccess]]

  /** Delete the cached snapshot (returns number of  removed */
  def deletePricingPlan(userId: String): F[Long]

  /** Legacy name kept: looks up by "token" but we treat it as userId */
  def lookupPricingPlan(userId: String): F[Option[PlanSnapshot]]
}

class PricingPlanCacheImpl[F[_] : Async : Logger](
  redisHost: String,
  redisPort: Int,
  appConfig: AppConfig
) extends PricingPlanCacheAlgebra[F] {

  // ---- Config ----------------------------------------------------------------

  /** TTL for plan cache; default 10 minutes if not present in your config */
  private val planTtl: FiniteDuration =
    Option(appConfig.pricingPlanConfig.cacheTtlMinutes).map(_.minutes).getOrElse(10.minutes)

  private def redisUri = s"redis://$redisHost:$redisPort"

  // private def userId String) = s"user:$userId:plan"

  private def withRedis[A](fa: RedisCommands[F, String, String] => F[A]): F[A] =
    Logger[F].debug(s"[PlanCache] Redis URI: $redisUri") *>
      Redis[F].utf8(redisUri).use(fa)

  // ---- Reads -----------------------------------------------------------------

  override def getPricingPlanCookieOnly(userId: String): F[Option[String]] =
    Logger[F].debug(s"[PlanCache] GET raw for userId=$userId") *>
      withRedis(_.get(userId)).flatTap {
        case Some(_) => Logger[F].debug(s"[PlanCache] HIT raw for userId=$userId")
        case None    => Logger[F].debug(s"[PlanCache] MISS raw for userId=$userId")
      }

  override def getPricingPlan(userId: String): F[Option[PlanSnapshot]] =
    Logger[F].debug(s"[PlanCache] GET snapshot for userId=$userId") *>
      withRedis(_.get(userId)).flatMap {
        case None =>
          Logger[F].debug(s"[PlanCache] MISS snapshot for userId=$userId").as(None)
        case Some(jsonStr) =>
          decode[PlanSnapshot](jsonStr) match {
            case Right(snap) =>
              Logger[F].debug(s"[PlanCache] HIT snapshot for userId=$userId").as(Some(snap))
            case Left(err) =>
              Logger[F].error(err)(s"[PlanCache] JSON decode failed for userId=$userId; evicting") *>
                withRedis(_.del(userId)).void.as(None)
          }
      }

  override def lookupPricingPlan(userId: String): F[Option[PlanSnapshot]] =
    getPricingPlan(userId) // legacy method name; same behavior

  // ---- Writes ----------------------------------------------------------------

  override def storeOnlyCookie(userId: String, json: String): F[Unit] =
    Logger[F].debug(s"[PlanCache] SET raw for userId=$userId ttl=$planTtl") *>
      withRedis(_.setEx(userId, json, planTtl)).void

  override def storePricingPlan(
    userId: String,
    snapshot: Option[PlanSnapshot]
  ): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    snapshot match {
      case Some(snap) =>
        Logger[F].debug(s"[PlanCache] SET snapshot for userId=$userId ttl=$planTtl") *>
          withRedis(_.setEx(userId, snap.asJson.noSpaces, planTtl)) *>
          Logger[F].debug(s"[PlanCache] OK snapshot set for userId=$userId").as(Valid(CacheUpdateSuccess))
      case None =>
        Logger[F].debug(s"[PlanCache] No snapshot provided, skipping set for userId=$userId") *>
          Validated.invalidNel(CacheUpdateFailure).pure[F]
    }

  override def updatePricingPlan(
    userId: String,
    snapshot: Option[PlanSnapshot]
  ): F[ValidatedNel[CacheErrors, CacheSuccess]] =
    storePricingPlan(userId, snapshot) // same semantics

  override def deletePricingPlan(userId: String): F[Long] =
    Logger[F].debug(s"[PlanCache] DEL snapshot for userId=$userId") *>
      withRedis(_.del(userId)).flatTap { deleted =>
        if (deleted > 0)
          Logger[F].debug(s"[PlanCache] Deleted plan snapshot for userId=$userId")
        else
          Logger[F].debug(s"[PlanCache] No plan snapshot to delete for userId=$userId")
      }
}

object PricingPlanCache {
  
  import dev.profunktor.redis4cats.effect.Log.Stdout.given // verbose Redis logs; swap to NoOp to silence

  def apply[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    appConfig: AppConfig
  ): PricingPlanCacheAlgebra[F] =
    new PricingPlanCacheImpl[F](redisHost, redisPort, appConfig)

  def make[F[_] : Async : Logger](
    redisHost: String,
    redisPort: Int,
    appConfig: AppConfig
  ): Resource[F, PricingPlanCacheAlgebra[F]] =
    Resource.pure(apply(redisHost, redisPort, appConfig))
}
