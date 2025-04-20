package controllers.business

import cats.effect.*
import controllers.constants.BusinessListingControllerConstants.*
import controllers.fragments.business.BusinessAddressRepoFragments.*
import controllers.fragments.business.BusinessContactDetailsRepoFragments.*
import controllers.fragments.business.BusinessSpecificationsRepoFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import io.circe.Json
import java.time.LocalDateTime
import models.business_listing.BusinessListingCard
import models.business_listing.InitiateBusinessListingRequest
import models.responses.CreatedResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.Server
import org.http4s.Method.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import repositories.business.BusinessAddressRepository
import repositories.business.BusinessContactDetailsRepository
import repositories.business.BusinessSpecificationsRepository
import services.business.BusinessListingService
import shared.HttpClientResource
import shared.TransactorResource
import testData.BusinessTestConstants.*
import testData.TestConstants.*
import weaver.*

class BusinessListingControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createBusinessAddressTable.update.run.transact(transactor.xa).void *>
          resetBusinessAddressTable.update.run.transact(transactor.xa).void *>
          createBusinessContactDetailsTable.update.run.transact(transactor.xa).void *>
          resetBusinessContactDetailsTable.update.run.transact(transactor.xa).void *>
          createBusinessSpecsTable.update.run.transact(transactor.xa).void *>
          resetBusinessSpecsTable.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "POST - /dev-quest-service/business/businesses/listing/initiate - " +
      "should generate the business listing data for a business in the respective tables, returning Created response"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val requestBody =
      InitiateBusinessListingRequest(
        userId = "",
        businessId = "BUS9999",
        businessName = "Busines 9999",
        description = "some desc"
      ).asJson

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/listing/initiate")
        .withEntity(requestBody)

    val expectedBody =
      BusinessListingCard(
        businessId = "BUS9999",
        businessName = "Busines 9999",
        description = "some desc"
      )

    client.run(request).use { response =>
      response.as[BusinessListingCard].map { body =>
        expect.all(
          response.status == Status.Created,
          body == expectedBody
        )
      }
    }
  }
}
