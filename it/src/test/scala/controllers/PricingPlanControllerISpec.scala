package controllers

import cats.effect.*
import cats.effect.IO
import cats.implicits.*
import controllers.fragments.PricingPlanControllerFragments.*
import controllers.fragments.UserPricingPlanControllerFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import fs2.text.lines
import fs2.text.utf8Decode
import fs2.Stream
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime
import models.auth.UserSession
import models.database.*
import models.languages.*
import models.pricing.Active
import models.pricing.PlanFeatures
import models.pricing.PlanSnapshot
import models.pricing.PricingPlanRow
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.Client
import org.http4s.implicits.*
import org.http4s.Method.*
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import scala.collection.immutable.ArraySeq
import shared.HttpClientResource
import shared.TransactorResource
import testData.ITestConstants.*
import weaver.*

class PricingPlanControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        dropPricingPlanTable.update.run.transact(transactor.xa).void *>
          dropUserPlansTable.update.run.transact(transactor.xa).void *>
          createPricingPlanTable.update.run.transact(transactor.xa).void *>
          createUserPlans.update.run.transact(transactor.xa).void *>
          resetUserPricingPlanTables.update.run.transact(transactor.xa).void *>
          insertPricingPlanData.update.run.transact(transactor.xa).void *>
          seedUserPlans.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  val sessionToken = "test-session-token"

  def fakeUserSession(clientId: String) =
    UserSession(
      userId = "USER001",
      cookieValue = sessionToken,
      email = "fakeEmail@gmail.com",
      userType = "Dev"
    )

  test(
    "GET - /dev-quest-service/billing/plans - should find the all of the active pricing plans available for a given user id shoyld"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"
    val fixedCurrentPeriodEnd: Instant = Instant.parse("2025-01-02T00:00:00Z")

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/billing/plans/USER001")
        .addCookie("auth_session", sessionToken)

    client.run(request).use { response =>
      response.as[List[PricingPlanRow]].map { body =>
        expect.all(
          response.status == Status.Ok,
          body.size == 3
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/billing/me/plan - should find the pricing plan for a given user id, returning OK and the correct pricing plan json body for the user"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val sessionToken = "test-session-token"
    val fixedCurrentPeriodEnd: Instant = Instant.parse("2025-01-02T00:00:00Z")

    val planFeatures =
      PlanFeatures(
        maxActiveQuests = Some(1),
        devPool = Some("invite"),
        estimations = Some(true),
        canCustomizeLevelThresholds = Some(true),
        boostQuests = Some(true),
        showOnLeaderBoard = Some(true),
        communicateWithClient = Some(true)
      )

    val testPricingPlan =
      PlanSnapshot(
        userId = "USER001",
        planId = "PLAN123",
        status = Active,
        features = planFeatures,
        currentPeriodEnd = Some(fixedCurrentPeriodEnd),
        cancelAtPeriodEnd = false
      )

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/billing/me/plan/USER001")
        .addCookie("auth_session", sessionToken)

    val expectedPricingPlan = testPricingPlan

    client.run(request).use { response =>
      response.as[PlanSnapshot].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedPricingPlan
        )
      }
    }
  }

}
