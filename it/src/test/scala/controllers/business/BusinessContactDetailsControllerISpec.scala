package controllers.business

import cats.effect.*
import com.comcast.ip4s.ipv4
import com.comcast.ip4s.port
import configuration.models.AppConfig
import controllers.constants.BusinessContactDetailsControllerConstants.*
import controllers.fragments.business.BusinessContactDetailsRepoFragments.*
import controllers.ControllerISpecBase
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.syntax.*
import io.circe.Json
import java.time.LocalDateTime
import models.business.contact_details.BusinessContactDetails
import models.business.contact_details.BusinessContactDetailsPartial
import models.business.contact_details.CreateBusinessContactDetailsRequest
import models.business.specifications.BusinessSpecifications
import models.database.*
import models.responses.CreatedResponse
import models.responses.DeletedResponse
import models.responses.ErrorResponse
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
import repositories.business.BusinessContactDetailsRepository
import repositories.business.BusinessSpecificationsRepository
import services.business.BusinessContactDetailsService
import shared.HttpClientResource
import shared.TransactorResource
import weaver.*

class BusinessContactDetailsControllerISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {
  type Res = (TransactorResource, HttpClientResource)

  def sharedResource: Resource[IO, Res] =
    for {
      transactor <- global.getOrFailR[TransactorResource]()
      _ <- Resource.eval(
        createBusinessContactDetailsTable.update.run.transact(transactor.xa).void *>
          resetBusinessContactDetailsTable.update.run.transact(transactor.xa).void *>
          insertBusinessContactDetailsTable.update.run.transact(transactor.xa).void
      )
      client <- global.getOrFailR[HttpClientResource]()
    } yield (transactor, client)

  test(
    "GET - /dev-quest-service/business/businesses/contact/details/businessId1 - " +
      "given a business_id, find the business contactDetails data for given id, returning OK and the contact details json"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/contact/details/businessId1")

    val expectedBusinessContactDetails = testBusinessContactDetails("userId1", "businessId1")

    client.run(request).use { response =>
      response.as[BusinessContactDetailsPartial].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBusinessContactDetails
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/business/businesses/contact/details/id_does_not_exists - " +
      "given a business id for data which does not exist, return NotFound and "
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/contact/details/id_does_not_exists")

    val expectedResponseBody = ErrorResponse("error", "error message")

    client.run(request).use { response =>
      response.as[ErrorResponse].map { body =>
        expect.all(
          response.status == Status.NotFound,
          body == expectedResponseBody
        )
      }
    }
  }

  test(
    "GET - /dev-quest-service/bad/url - " +
      "given an error in the url, return NotFound and appropriate body"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](GET, uri"http://127.0.0.1:9999/dev-quest-service/bad/url")

    val expectedResponseBody = "Not found"

    client.run(request).use { response =>
      response.bodyText.compile.string.map { body =>
        expect.all(
          response.status == Status.NotFound,
          body == expectedResponseBody
        )
      }
    }
  }

  test(
    "POST - /dev-quest-service/business/businesses/contact/details/create - " +
      "should generate the business contact data for a business in database table, returning Created response"
  ) { (transactorResource, log) =>

    val transactor = transactorResource._1.xa
    val client = transactorResource._2.client

    val businessAddressRequest: Json = testCreateBusinessContactDetailsRequest("user_id_3", "business_id_3").asJson

    val request =
      Request[IO](POST, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/contact/details/create")
        .withEntity(businessAddressRequest)

    val expectedBody = CreatedResponse(CreateSuccess.toString, "Business contact details created successfully")

    client.run(request).use { response =>
      response.as[CreatedResponse].map { body =>
        expect.all(
          response.status == Status.Created,
          body == expectedBody
        )
      }
    }
  }

  test(
    "DELETE - /dev-quest-service/business/businesses/contact/details/business_id_2 - " +
      "given a business_id, delete the business contact details data for given business id, returning OK and Deleted response json"
  ) { (sharedResources, log) =>

    val transactor = sharedResources._1.xa
    val client = sharedResources._2.client

    val request =
      Request[IO](DELETE, uri"http://127.0.0.1:9999/dev-quest-service/business/businesses/contact/details/business_id_2")

    val expectedBody = DeletedResponse(DeleteSuccess.toString, "Business contact details deleted successfully")

    client.run(request).use { response =>
      response.as[DeletedResponse].map { body =>
        expect.all(
          response.status == Status.Ok,
          body == expectedBody
        )
      }
    }
  }

}
