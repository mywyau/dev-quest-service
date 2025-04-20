package controllers.business

import cats.effect.*
import controllers.constants.BusinessListingControllerConstants.*
import controllers.fragments.business.BusinessAddressRepoFragments.*
import controllers.fragments.business.BusinessContactDetailsRepoFragments.*
import controllers.fragments.business.BusinessSpecificationsRepoFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import io.circe.syntax.*
import io.circe.Json
import models.business_listing.BusinessListing
import models.business_listing.BusinessListingCard
import models.responses.DeletedResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.*
import org.http4s.Method.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class DeleteAllBusinessListingControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createBusinessAddressTable.update.run.transact(transactor.xa).void *>
          resetBusinessAddressTable.update.run.transact(transactor.xa).void *>
          insertSameUserIdBusinessAddressData.update.run.transact(transactor.xa).void *>

          createBusinessContactDetailsTable.update.run.transact(transactor.xa).void *>
          resetBusinessContactDetailsTable.update.run.transact(transactor.xa).void *>
          sameUserIdBusinessContactDetailsData.update.run.transact(transactor.xa).void *>

          createBusinessSpecsTable.update.run.transact(transactor.xa).void *>
          resetBusinessSpecsTable.update.run.transact(transactor.xa).void *>
          sameUserIdBusinessSpecsData.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "DELETE - /dev-quest-service/business/businesses/listing/delete/all/USER123 - should delete all data related to business listings for the given user id, returning OK - Deleted response"
  ) { (sharedResources, log) =>
    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    // Define the requests
    val findAllRequest =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/listing/cards/find/all") // TODO: Fix this endpoint to be user id based
      // Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/listing/cards/find/all/USER123")

    val deleteRequest =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/listing/delete/all/USER123")

    for {
      // Step 1: Verify initial listings
      // findAllResponseBefore <- client.run(findAllRequest).use(_.as[List[BusinessListingCard]])
      // _ <- IO(expect(findAllResponseBefore.size == 3))

      // Step 2: Perform the delete operation
      deleteResponse <- client.run(deleteRequest).use(_.as[DeletedResponse])
      _ <- IO(expect(deleteResponse.message == "All Business listings deleted successfully"))

      // Step 3: Verify all listings are deleted
      // findAllResponseAfter <- client.run(findAllRequest).use(_.as[List[BusinessListingCard]])
      // _ <- IO(expect(findAllResponseAfter.isEmpty))
    } yield success
  }

}
