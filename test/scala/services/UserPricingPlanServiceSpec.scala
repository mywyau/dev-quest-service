package services

import cache.PricingPlanCacheAlgebra
import cats.data.NonEmptyList
import cats.data.Validated
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import cats.syntax.validated.*
import configuration.constants.AppConfigConstants.appConfigConstant
import configuration.AppConfig
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime
import models.cache.*
import models.pricing.*
import models.pricing.UserPricingPlanStatus.*
import models.Client
import models.UserType
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repositories.PricingPlanRepositoryAlgebra
import repositories.UserPricingPlanRepositoryAlgebra
import services.stripe.*
import weaver.SimpleIOSuite

final class StubPricingPlanRepo(
  plansById: Map[String, PricingPlanRow],
  plansByPriceId: Map[String, PricingPlanRow] = Map.empty
) extends PricingPlanRepositoryAlgebra[IO] {

  override def listPlans(userType: UserType): IO[List[PricingPlanRow]] = IO.pure(plansById.values.toList.filter(_.userType == userType))
  
  override def byPlanId(id: String) = IO.pure(plansById.get(id))

  override def byStripePriceId(pid: String) = IO.pure(plansByPriceId.get(pid))
}

final class SpyUserPlanRepo(
  upsertsRef: Ref[IO, List[UserPlanUpsert]],
  setStatusRef: Ref[IO, List[(String, String, Option[Instant], Boolean)]],
  views: Map[String, UserPricingPlanView] = Map.empty,
  customerToUser: Map[String, String] = Map.empty
) extends UserPricingPlanRepositoryAlgebra[IO] {

  val fixedCurrentPeriodEnd: Instant = Instant.parse("2025-01-02T00:00:00Z")

  def upsert(up: UserPlanUpsert) =
    upsertsRef.update(up :: _) *> IO.pure(
      UserPricingPlanRow(up.userId, up.planId, up.stripeSubscriptionId, up.stripeCustomerId, up.status, fixedCurrentPeriodEnd, up.currentPeriodEnd, false)
    )

  def get(userId: String) = IO.pure(views.get(userId))

  def setStatus(u: String, s: String, cpe: Option[Instant], cap: Boolean) =
    setStatusRef.update(((u, s, cpe, cap)) :: _) *> IO.pure(
      UserPricingPlanRow(u, "client_free", None, None, UserPricingPlanStatus.fromString(s), fixedCurrentPeriodEnd, cpe, cap)
    )

  def findUserIdByStripeCustomerId(c: String) = IO.pure(customerToUser.get(c))
}

final class StubPlanCache(
  storeRef: Ref[IO, Map[String, PlanSnapshot]],
  delRef: Ref[IO, List[String]],
  initial: Map[String, PlanSnapshot] = Map.empty
) extends PricingPlanCacheAlgebra[IO] {

  val ttlResultNel: Validated[NonEmptyList[CacheErrors], CacheSuccess] = Validated.valid(CacheUpdateSuccess)

  def getPricingPlanCookieOnly(userId: String) = IO.pure(None)

  def getPricingPlan(key: String) = storeRef.get.map(_.get(key).orElse(initial.get(key)))

  def storeOnlyCookie(userId: String, token: String) = IO.unit

  def storePricingPlan(key: String, snap: Option[PlanSnapshot]) = snap.fold(IO.pure(ttlResultNel))(s => storeRef.update(_ + (key -> s)) as ttlResultNel)

  def updatePricingPlan(key: String, snap: Option[PlanSnapshot]) = storePricingPlan(key, snap)

  def deletePricingPlan(key: String) = delRef.update(key :: _) as 1L

  def lookupPricingPlan(token: String) = IO.pure(None)
}

final class StubStripe(
  checkoutUrl: String = "https:example/checkout"
) extends StripeBillingServiceAlgebra[IO] {

  override def setCancelAtPeriodEnd(subscriptionId: String, cancel: Boolean): IO[StripeSubState] = ???
  def createCheckoutSession(u: String, p: String, s: String, c: String, i: String) = IO.pure(checkoutUrl)

  def createBillingPortalSession(c: String, r: String) = IO.pure("https:example/portal")
  def verifyAndParseEvent(raw: String, sig: String) = IO.raiseError(new Exception("unused"))
}

object UserPricingPlanServiceSpec extends SimpleIOSuite {

  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val fixedCurrentPeriodEnd: Instant = Instant.parse("2025-01-02T00:00:00Z")

  val freePlanFeatures =
    PlanFeatures(
      maxActiveQuests = Some(2),
      devPool = None,
      estimations = Some(true),
      canCustomizeLevelThresholds = Some(false),
      boostQuests = Some(false),
      showOnLeaderBoard = Some(true),
      communicateWithClient = Some(false)
    )

  val freePlan =
    PricingPlanRow(
      planId = "client_free",
      name = "Free",
      description = Some(".."),
      price = 0,
      interval = "month",
      userType = Client,
      stripePriceId = None,
      features = freePlanFeatures,
      createdAt = LocalDateTime.now
    )

  val paidPlan =
    freePlan.copy(
      planId = "client_growth",
      name = "Growth",
      price = 50,
      stripePriceId = Some("price_123")
    )

  def viewFor(uid: String, p: PricingPlanRow) = UserPricingPlanView(
    userPlanRow = UserPricingPlanRow(uid, p.planId, None, Some("cus_001"), Active, fixedCurrentPeriodEnd, None, false),
    planRow = p
  )

  val appConfig = appConfigConstant

  test("getSnapshot cache miss → load from repo + store") {
    for {
      storeRef <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)
      delRef <- Ref.of[IO, List[String]](Nil)
      repo = new SpyUserPlanRepo(Ref.unsafe(Nil), Ref.unsafe(Nil), views = Map("u1" -> viewFor("u1", freePlan)))
      cache = new StubPlanCache(storeRef, delRef)
      catalog = new StubPricingPlanRepo(Map(freePlan.planId -> freePlan, paidPlan.planId -> paidPlan))
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
      snap <- svc.getSnapshot("u1")
      stored <- storeRef.get
    } yield {
      val key = "user:u1:plan"
      expect(snap.planId == "client_free") &&
      expect(stored.contains(key))
    }
  }

  test(".switchToFree - upserts and invalidates cache") {
    for {
      upsertsRef <- Ref.of[IO, List[UserPlanUpsert]](Nil)
      setsRef <- Ref.of[IO, List[(String, String, Option[Instant], Boolean)]](Nil)
      delRef <- Ref.of[IO, List[String]](Nil)
      storeRef <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)
      repo = new SpyUserPlanRepo(upsertsRef, setsRef)
      cache = new StubPlanCache(storeRef, delRef)
      catalog = new StubPricingPlanRepo(Map(freePlan.planId -> freePlan, paidPlan.planId -> paidPlan))
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
      _ <- svc.switchToFree("u2", "client_free")
      upserts <- upsertsRef.get
      dels <- delRef.get
    } yield expect(upserts.nonEmpty && upserts.head.planId == "client_free") &&
      expect(dels.contains("user:u2:plan"))
  }

  test(".applyStripeWebhook - price present → upsert + cache invalidate") {

    val evt =
      StripeEvent(
        eventType = "checkout.session.completed",
        userIdHint = Some("u3"),
        customerId = Some("cus_3"),
        subscriptionId = Some("sub_3"),
        priceId = Some("price_123"),
        status = Some("active"),
        currentPeriodEnd = Some(Instant.now.plusSeconds(3600)),
        cancelAtPeriodEnd = Some(false)
      )

    for {
      upsertsRef <- Ref.of[IO, List[UserPlanUpsert]](Nil)
      setsRef <- Ref.of[IO, List[(String, String, Option[Instant], Boolean)]](Nil)
      delRef <- Ref.of[IO, List[String]](Nil)
      storeRef <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)
      repo = new SpyUserPlanRepo(upsertsRef, setsRef)
      cache = new StubPlanCache(storeRef, delRef)
      catalog = new StubPricingPlanRepo(
        plansById = Map(freePlan.planId -> freePlan, paidPlan.planId -> paidPlan),
        plansByPriceId = Map("price_123" -> paidPlan)
      )
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
      _ <- svc.applyStripeWebhook(evt)
      upserts <- upsertsRef.get
      dels <- delRef.get
    } yield expect(upserts.exists(_.planId == "client_growth")) &&
      expect(dels.contains("user:u3:plan"))
  }

  test("getSnapshot cache hit → returns cached snapshot and does not write") {

    val freePlanFeatures =
      PlanFeatures(
        maxActiveQuests = Some(2),
        devPool = None,
        estimations = Some(true),
        canCustomizeLevelThresholds = Some(false),
        boostQuests = Some(false),
        showOnLeaderBoard = Some(true),
        communicateWithClient = Some(false)
      )

    val cached = PlanSnapshot("u0", "client_free", Active, freePlanFeatures, None, false)

    for {
      storeRef <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty) // writes end up here
      delRef <- Ref.of[IO, List[String]](Nil)
      // seed the cache via the StubPlanCache's `initial`
      cache = new StubPlanCache(storeRef, delRef, initial = Map("user:u0:plan" -> cached))
      repo = new SpyUserPlanRepo(Ref.unsafe(Nil), Ref.unsafe(Nil)) // should not be used
      catalog = new StubPricingPlanRepo(Map(freePlan.planId -> freePlan, paidPlan.planId -> paidPlan))
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)

      snap <- svc.getSnapshot("u0")
      writes <- storeRef.get
    } yield expect.same(snap, cached) && // got what was in cache
      expect(writes.isEmpty) // no additional store happened
  }

  test("getSnapshot cache miss → no DB plan → raises") {
    for {
      storeRef <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)
      delRef <- Ref.of[IO, List[String]](Nil)
      cache = new StubPlanCache(storeRef, delRef)
      // repo.get returns None
      repo = new SpyUserPlanRepo(Ref.unsafe(Nil), Ref.unsafe(Nil), views = Map.empty)
      catalog = new StubPricingPlanRepo(Map.empty)
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
      res <- svc.getSnapshot("u-missing").attempt
    } yield expect(res.isLeft)
  }

  test("createCheckout: paid plan → returns Stripe Checkout URL") {
    val svc = {
      val cache = new StubPlanCache(Ref.unsafe(Map.empty), Ref.unsafe(Nil))
      val repo = new SpyUserPlanRepo(Ref.unsafe(Nil), Ref.unsafe(Nil))
      val catalog = new StubPricingPlanRepo(Map(paidPlan.planId -> paidPlan))
      val stripe = new StubStripe("https://example/checkout-url")
      new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
    }

    svc.createCheckout("u7", "client_growth", "idem-1").map { url =>
      expect(url == "https://example/checkout-url")
    }
  }

  test("createCheckout: free plan (no price) → rejects") {
    val svc = {
      val cache = new StubPlanCache(Ref.unsafe(Map.empty), Ref.unsafe(Nil))
      val repo = new SpyUserPlanRepo(Ref.unsafe(Nil), Ref.unsafe(Nil))
      val catalog = new StubPricingPlanRepo(Map(freePlan.planId -> freePlan)) // price = 0
      val stripe = new StubStripe()
      new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
    }

    svc.createCheckout("u8", "client_free", "idem-2").attempt.map { e =>
      expect(e.isLeft)
    }
  }

  test("applyStripeWebhook: no userIdHint → resolves via customerId → upsert + invalidate") {
    val evt = StripeEvent(
      eventType = "invoice.paid",
      userIdHint = None,
      customerId = Some("cus_42"),
      subscriptionId = Some("sub_42"),
      priceId = Some("price_123"),
      status = Some("active"), // lower-case stripe value
      currentPeriodEnd = Some(Instant.now.plusSeconds(600)),
      cancelAtPeriodEnd = Some(false)
    )

    for {
      upRef <- Ref.of[IO, List[UserPlanUpsert]](Nil)
      stRef <- Ref.of[IO, List[(String, String, Option[Instant], Boolean)]](Nil)
      delRef <- Ref.of[IO, List[String]](Nil)
      store <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)

      repo = new SpyUserPlanRepo(upRef, stRef, views = Map.empty, customerToUser = Map("cus_42" -> "u42"))
      cache = new StubPlanCache(store, delRef)
      catalog = new StubPricingPlanRepo(plansById = Map(paidPlan.planId -> paidPlan), plansByPriceId = Map("price_123" -> paidPlan))
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)

      _ <- svc.applyStripeWebhook(evt)
      ups <- upRef.get
      dels <- delRef.get
    } yield expect(ups.exists(up => up.userId == "u42" && up.planId == "client_growth")) &&
      expect(dels.contains("user:u42:plan"))
  }

  test("applyStripeWebhook: no userIdHint → resolves via customerId → upsert + invalidate") {

    val evt = StripeEvent(
      eventType = "invoice.paid",
      userIdHint = None,
      customerId = Some("cus_42"),
      subscriptionId = Some("sub_42"),
      priceId = Some("price_123"),
      status = Some("active"), // lower-case stripe value
      currentPeriodEnd = Some(Instant.now.plusSeconds(600)),
      cancelAtPeriodEnd = Some(false)
    )

    for {
      upRef <- Ref.of[IO, List[UserPlanUpsert]](Nil)
      stRef <- Ref.of[IO, List[(String, String, Option[Instant], Boolean)]](Nil)
      delRef <- Ref.of[IO, List[String]](Nil)
      store <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)

      repo = new SpyUserPlanRepo(upRef, stRef, views = Map.empty, customerToUser = Map("cus_42" -> "u42"))
      cache = new StubPlanCache(store, delRef)
      catalog = new StubPricingPlanRepo(
        plansById = Map(paidPlan.planId -> paidPlan),
        plansByPriceId = Map("price_123" -> paidPlan)
      )
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)

      _ <- svc.applyStripeWebhook(evt)
      ups <- upRef.get
      dels <- delRef.get
    } yield expect(ups.exists(up => up.userId == "u42" && up.planId == "client_growth")) &&
      expect(dels.contains("user:u42:plan"))
  }

  test("applyStripeWebhook: no price + lowercase status → setStatus + invalidate") {
    val evt = StripeEvent(
      eventType = "customer.subscription.updated",
      userIdHint = Some("u9"),
      customerId = Some("cus_9"),
      subscriptionId = Some("sub_9"),
      priceId = None,
      status = Some("canceled"), // stripe’s lowercase
      currentPeriodEnd = Some(Instant.now.plusSeconds(3600)),
      cancelAtPeriodEnd = Some(true)
    )

    for {
      upRef <- Ref.of[IO, List[UserPlanUpsert]](Nil)
      stRef <- Ref.of[IO, List[(String, String, Option[Instant], Boolean)]](Nil)
      del <- Ref.of[IO, List[String]](Nil)
      store <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)

      repo = new SpyUserPlanRepo(upRef, stRef)
      cache = new StubPlanCache(store, del)
      catalog = new StubPricingPlanRepo(Map.empty)
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)

      _ <- svc.applyStripeWebhook(evt)
      sets <- stRef.get
      dels <- del.get
    } yield {
      val called = sets.headOption.exists { case (u, st, _, cap) => u == "u9" && st == "Canceled" && cap }
      expect(called) && expect(dels.contains("user:u9:plan"))
    }
  }

  test("applyStripeWebhook: price present but status missing → defaults to Active") {
    val evt = StripeEvent(
      eventType = "invoice.paid",
      userIdHint = Some("ua"),
      customerId = Some("cus_a"),
      subscriptionId = Some("sub_a"),
      priceId = Some("price_123"),
      status = None,
      currentPeriodEnd = None,
      cancelAtPeriodEnd = None
    )

    for {
      upRef <- Ref.of[IO, List[UserPlanUpsert]](Nil)
      del <- Ref.of[IO, List[String]](Nil)
      store <- Ref.of[IO, Map[String, PlanSnapshot]](Map.empty)

      repo = new SpyUserPlanRepo(upRef, Ref.unsafe(Nil))
      cache = new StubPlanCache(store, del)
      catalog = new StubPricingPlanRepo(
        plansById = Map(paidPlan.planId -> paidPlan),
        plansByPriceId = Map("price_123" -> paidPlan)
      )
      stripe = new StubStripe()
      svc = new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)

      _ <- svc.applyStripeWebhook(evt)
      up <- upRef.get
    } yield {
      val ok = up.headOption.exists(_.status == Active)
      expect(ok)
    }
  }

  test("switchToFree: unknown planId → error") {
    val svc = {
      val cache = new StubPlanCache(Ref.unsafe(Map.empty), Ref.unsafe(Nil))
      val repo = new SpyUserPlanRepo(Ref.unsafe(Nil), Ref.unsafe(Nil))
      val catalog = new StubPricingPlanRepo(Map.empty) // unknown plan
      val stripe = new StubStripe()
      new UserPricingPlanServiceImpl[IO](appConfig, cache, repo, catalog, stripe)
    }

    svc.switchToFree("ux", "does_not_exist").attempt.map(e => expect(e.isLeft))
  }

}
