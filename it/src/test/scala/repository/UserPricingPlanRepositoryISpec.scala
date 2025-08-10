// it/src/test/scala/repository/UserPricingPlanRepositoryISpec.scala
package repository

import cats.effect.IO
import cats.effect.Resource
import doobie.*
import doobie.implicits.*
import models.pricing.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.PricingPlanRepositoryImpl
import repositories.UserPricingPlanRepositoryImpl
import repository.fragments.PricingPlanRepoFragments.*
import repository.fragments.UserPricingPlanRepoFragments.*
import shared.TransactorResource
import weaver.*

import java.time.Instant
import java.time.LocalDateTime

class UserPricingPlanRepositoryISpec(global: GlobalRead) extends IOSuite with RepositoryISpecBase {

  type Res = UserPricingPlanRepositoryImpl[IO]

  private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
    Resource.eval(
      dropPricingPlanTable.update.run.transact(transactor.xa).void *>
        dropUserPlansTable.update.run.transact(transactor.xa).void *>
        createPricingPlanTable.update.run.transact(transactor.xa).void *>
        createUserPlans.update.run.transact(transactor.xa).void *>
        resetPricingPlanTables.update.run.transact(transactor.xa).void *>
        insertPricingPlanData.update.run.transact(transactor.xa).void *>
        seedUserPlans.update.run.transact(transactor.xa).void
    )

  def sharedResource: Resource[IO, UserPricingPlanRepositoryImpl[IO]] =
    for {
      tr <- global.getOrFailR[TransactorResource]()
      _ <- initializeSchema(tr)
    } yield new UserPricingPlanRepositoryImpl[IO](tr.xa)

  test(".get(userId) - returns joined view with plan/feature fields") { repo =>
    for {
      viewOpt <- repo.get("u1")
    } yield {
      val v = viewOpt.getOrElse(sys.error("expected view for u1"))

      expect.same(v.userPlanRow.userId, "u1") &&
      expect.same(v.userPlanRow.planId, "PLAN002") &&
      expect(v.userPlanRow.status == Active) &&
      expect.same(v.planRow.planId, "PLAN002") &&
      expect.same(v.planRow.name, "ClientStarter") &&
      // sanity on decoded features for PLAN002 from your seed
      expect(v.planRow.features.maxActiveQuests.contains(5)) &&
      expect(v.planRow.features.devPool.contains("invite")) &&
      expect(v.planRow.features.estimations.contains(true))
    }
  }

  test(".get(userId) - returns None for unknown user") { repo =>
    repo.get("nope").map(v => expect(v.isEmpty))
  }

  test(".upsert - inserts a new user plan") { repo =>
    val up = UserPlanUpsert(
      userId = "u_insert",
      planId = "PLAN003", // ClientGrowth
      stripeSubscriptionId = Some("sub_new"),
      stripeCustomerId = Some("cus_new"),
      status = Active,
      currentPeriodEnd = Some(Instant.parse("2025-04-01T00:00:00Z"))
    )

    for {
      row <- repo.upsert(up)
      view <- repo.get("u_insert")
    } yield expect.same(row.userId, "u_insert") &&
      expect.same(row.planId, "PLAN003") &&
      expect(row.stripeSubscriptionId.contains("sub_new")) &&
      expect(row.stripeCustomerId.contains("cus_new")) &&
      expect(row.status == Active) &&
      // joined view agrees:
      expect(view.exists(_.planRow.planId == "PLAN003"))
  }

  test(".upsert - on conflict updates existing row (plan/status/sub/customer/cpe)") { repo =>
    val first = UserPlanUpsert(
      userId = "u_conflict",
      planId = "PLAN001",
      stripeSubscriptionId = None,
      stripeCustomerId = Some("cus_conf"),
      status = Active,
      currentPeriodEnd = None
    )
    val update = first.copy(
      planId = "PLAN004", // ClientScale
      stripeSubscriptionId = Some("sub_updated"),
      status = Canceled,
      currentPeriodEnd = Some(Instant.parse("2025-05-01T00:00:00Z"))
    )

    for {
      _ <- repo.upsert(first)
      r2 <- repo.upsert(update)
      v2 <- repo.get("u_conflict")
    } yield expect.same(r2.planId, "PLAN004") &&
      expect(r2.stripeSubscriptionId.contains("sub_updated")) &&
      expect(r2.status == Canceled) &&
      expect(v2.exists(_.planRow.planId == "PLAN004"))
  }

  test(".setStatus - updates status/cancel flags/currentPeriodEnd") { repo =>
    // seed has u2 with PLAN006
    val newEnd = Some(Instant.parse("2026-01-01T00:00:00Z"))
    for {
      after <- repo.setStatus(
        userId = "u2",
        status = "Canceled",
        currentPeriodEnd = newEnd,
        cancelAtPeriodEnd = true
      )
      v <- repo.get("u2")
    } yield expect(after.status == Canceled) &&
      expect(after.cancelAtPeriodEnd) &&
      expect(after.currentPeriodEnd == newEnd) &&
      // plan stays same:
      expect(v.exists(_.planRow.planId == "PLAN006"))
  }

  test(".findUserIdByStripeCustomerId - returns latest by started_at") { repo =>
    // cus_dup has two rows: old_user (2025-01-01) and new_user (2025-03-01)
    repo.findUserIdByStripeCustomerId("cus_dup").map { uidOpt =>
      expect.same(uidOpt, Some("new_user"))
    }
  }
}
