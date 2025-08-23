// controllers/mocks/MockPricingPlanCache.scala
package controllers.mocks

import infrastructure.cache.PricingPlanCacheAlgebra
import cats.data.Validated
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.*
import cats.syntax.all.*
import models.cache.*
import models.pricing.PlanSnapshot

final class MockPricingPlanCache(ref: Ref[IO, Map[String, PlanSnapshot]]) extends PricingPlanCacheAlgebra[IO] {

  // Not used in these tests — no-op
  def getPricingPlanCookieOnly(userId: String): IO[Option[String]] = IO.pure(None)
  def storeOnlyCookie(userId: String, token: String): IO[Unit] = IO.unit

  // IMPORTANT: 'key' is already "user:<id>:plan". Do NOT prepend again.
  def getPricingPlan(key: String): IO[Option[PlanSnapshot]] =
    ref.get.map(_.get(key))

  def storePricingPlan(key: String, snapshot: Option[PlanSnapshot]): IO[ValidatedNel[CacheErrors, CacheSuccess]] =
    snapshot match {
      case Some(s) => ref.update(_ + (key -> s)).as(Valid(CacheCreateSuccess))
      case None => IO.pure(Valid(CacheCreateSuccess))
    }

  def updatePricingPlan(key: String, snapshot: Option[PlanSnapshot]): IO[ValidatedNel[CacheErrors, CacheSuccess]] =
    snapshot match {
      case Some(s) => ref.update(_ + (key -> s)).as(Valid(CacheUpdateSuccess))
      case None => IO.pure(Valid(CacheUpdateSuccess))
    }

  def deletePricingPlan(key: String): IO[Long] =
    ref.modify { m =>
      val existed = m.contains(key)
      (m - key, if (existed) 1L else 0L)
    }

  // If you use this in tests, treat arg as the exact key as well
  def lookupPricingPlan(key: String): IO[Option[PlanSnapshot]] =
    ref.get.map(_.get(key))
}
