package repository

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import java.time.LocalDateTime
import models.database.*
import models.pricing.*
import models.Client
import models.Dev
import repositories.PricingPlanRepositoryImpl
import repository.fragments.PricingPlanRepoFragments.*
import repository.fragments.UserPricingPlanRepoFragments.*
import repository.RepositoryISpecBase
import scala.collection.immutable.ArraySeq
import shared.TransactorResource
import testData.ITestConstants.*
import weaver.GlobalRead
import weaver.IOSuite
import weaver.ResourceTag

class PricingPlanRepositoryISpec(global: GlobalRead) extends IOSuite with RepositoryISpecBase {

  type Res = PricingPlanRepositoryImpl[IO]

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

  def sharedResource: Resource[IO, PricingPlanRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      questRepo = new PricingPlanRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield questRepo

    setup
  }

  test(".listPlans(Dev) - returns all the seeded Dev Pricing Plans in price order and with expected fields") { pricingRepo =>
    for {
      plans <- pricingRepo.listPlans(Dev)
    } yield {

      val ids = plans.map(_.planId)

      val expectedIds =
        List("PLAN005", "PLAN006", "PLAN007")

      val devFreePlan: PricingPlanRow = plans.find(_.planId == "PLAN005").get

      expect(plans.size == 3) &&
      expect.same(ids, expectedIds) &&
      expect(devFreePlan.price == BigDecimal(0)) &&
      expect(devFreePlan.interval == "month") &&
      expect(devFreePlan.stripePriceId.contains("stripe_price_id_005")) &&
      expect(devFreePlan.features.maxActiveQuests.contains(1))
    }
  }

  test(".listPlans(Client) - returns all the seeded Client Pricing Plans in price order and with expected fields") { pricingRepo =>
    for {
      plans <- pricingRepo.listPlans(Client)
    } yield {

      val ids = plans.map(_.planId)

      val expectedIds =
        List("PLAN001", "PLAN002", "PLAN003", "PLAN004")

      val clientPlan = plans.find(_.planId == "PLAN002").get

      expect(plans.size == 4) &&
      expect.same(ids, expectedIds) &&
      expect(clientPlan.price == BigDecimal(30)) &&
      expect(clientPlan.interval == "month") &&
      expect(clientPlan.stripePriceId.contains("stripe_price_id_002")) &&
      expect(clientPlan.features.maxActiveQuests.contains(5)) &&
      expect(clientPlan.features.devPool.contains("invite")) &&
      expect(clientPlan.features.estimations.contains(true))
    }
  }

  // --- byPlanId: happy path (ClientGrowth) ---
  test(".byPlanId() finds a specific plan based on PlanId and decodes features - return Client plan - PLAN003") { repo =>
    for {
      planOpt <- repo.byPlanId("PLAN003") // ClientGrowth
    } yield {
      val p = planOpt.getOrElse(sys.error("PLAN003 not found"))

      expect.same(p.planId, "PLAN003") &&
      expect.same(p.name, "ClientGrowth") &&
      expect(p.price == BigDecimal(60)) &&
      expect.same(p.interval, "month") &&
      expect(p.userType == Client) &&
      // features for client growth:
      expect(p.features.maxActiveQuests.contains(20)) &&
      expect(p.features.devPool.contains("invite")) &&
      expect(p.features.estimations.contains(true)) &&
      expect(p.features.canCustomizeLevelThresholds.contains(true)) &&
      expect(p.features.boostQuests.contains(true)) &&
      // dev-only flags should be absent for client plans:
      expect(p.features.showOnLeaderBoard.isEmpty) &&
      expect(p.features.communicateWithClient.isEmpty)
    }
  }

  test(".byPlanId() returns None when planId does not exist") { repo =>
    for {
      none <- repo.byPlanId("DOES_NOT_EXIST")
    } yield expect(none.isEmpty)
  }

  // --- byStripePriceId: happy path (DevPro) ---
  test(".byStripePriceId() finds a specific pricing plan based on StripePriceId - return dev plan 'PLAN007' and decodes dev features") { repo =>
    for {
      planOpt <- repo.byStripePriceId("stripe_price_id_007") // DevPro
    } yield {
      val p = planOpt.getOrElse(sys.error("stripe_price_id_007 not found"))

      expect.same(p.planId, "PLAN007") &&
      expect.same(p.name, "DevPro") &&
      expect(p.price == BigDecimal(40)) &&
      expect.same(p.interval, "month") &&
      expect(p.userType == Dev) &&
      // dev features:
      expect(p.features.maxActiveQuests.contains(10)) &&
      expect(p.features.showOnLeaderBoard.contains(true)) &&
      expect(p.features.communicateWithClient.contains(true)) &&
      // client-only fields should be absent here:
      expect(p.features.devPool.isEmpty) &&
      expect(p.features.canCustomizeLevelThresholds.isEmpty) &&
      expect(p.features.boostQuests.isEmpty)
    }
  }

  // --- byStripePriceId: not found ---
  test(".byStripePriceId() returns None when price id does not exist") { repo =>
    for {
      none <- repo.byStripePriceId("price_missing")
    } yield expect(none.isEmpty)
  }

}
