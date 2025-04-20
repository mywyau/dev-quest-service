package controllers.office

import cats.effect.*
import controllers.constants.OfficeListingControllerConstants.*
import controllers.fragments.OfficeAddressRepoFragments.*
import controllers.fragments.OfficeContactDetailsRepoFragments.*
import controllers.fragments.OfficeSpecificationRepoFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import io.circe.syntax.*
import io.circe.Json
import models.office_listing.OfficeListing
import models.office_listing.OfficeListingCard
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

class DeleteAllOfficeListingControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {
  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createOfficeAddressTable.update.run.transact(transactor.xa).void *>
          resetOfficeAddressTable.update.run.transact(transactor.xa).void *>
          insertSameBusinessIdOfficeAddressData.update.run.transact(transactor.xa).void *>

          createOfficeContactDetailsTable.update.run.transact(transactor.xa).void *>
          resetOfficeContactDetailsTable.update.run.transact(transactor.xa).void *>
          insertSameBusinessIdOfficeContactDetailsData.update.run.transact(transactor.xa).void *>

          createOfficeSpecsTable.update.run.transact(transactor.xa).void *>
          resetOfficeSpecsTable.update.run.transact(transactor.xa).void *>
          insertSameBusinessIdOfficeSpecificationsData.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "DELETE - /dev-quest-service/business/office/listing/delete/all/BUS123 - should delete all data related to office listings for the given business id, returning OK deleted response"
  ) { (sharedResources, log) =>
    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    // Define the requests
    val findAllRequest =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/office/listing/cards/find/all/BUS123")

    val deleteRequest =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/office/listing/delete/all/BUS123")

    for {
      // Step 1: Verify initial listings
      findAllResponseBefore <- client.run(findAllRequest).use(_.as[List[OfficeListingCard]])
      _ <- IO(expect(findAllResponseBefore.size == 5))

      // Step 2: Perform the delete operation
      deleteResponse <- client.run(deleteRequest).use(_.as[DeletedResponse])
      _ <- IO(expect(deleteResponse.message == "All Office listings deleted successfully"))

      // Step 3: Verify all listings are deleted
      findAllResponseAfter <- client.run(findAllRequest).use(_.as[List[OfficeListingCard]])
      _ <- IO(expect(findAllResponseAfter.isEmpty))
    } yield success
  }

}
