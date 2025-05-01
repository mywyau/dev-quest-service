package controllers

import cats.effect.IO
import controllers.ControllerSpecBase
import controllers.business.BusinessAddressController
import controllers.business.business_address.BusinessAddressControllerConstants.*
import models.responses.ErrorResponse
import org.http4s.*
import org.http4s.Status.BadRequest
import org.http4s.Status.Ok
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.implicits.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import services.business.BusinessAddressServiceAlgebra
import weaver.SimpleIOSuite


object BusinessAddressControllerSpec extends SimpleIOSuite with ControllerSpecBase {

  def createUserController(businessAddressService: BusinessAddressServiceAlgebra[IO]): HttpRoutes[IO] =
    BusinessAddressController[IO](businessAddressService).routes

  test("POST - /business/businesses/address/details/ should return 201 when user is created successfully") {

    val mockBusinessAddressService = new MockBusinessAddressService(Map("userId1" -> sampleBusinessAddress1))

    val controller = createUserController(mockBusinessAddressService)

    val request = Request[IO](Method.GET, uri"/business/businesses/address/details/userId1")

    for {
      response <- controller.orNotFound.run(request)
    } yield expect(response.status == Ok)
  }

  test("POST - /business/address/details/ - should return 400 when a user id is not found") {

    val mockBusinessAddressService = new MockBusinessAddressService(Map())

    val controller = createUserController(mockBusinessAddressService)

    val request = Request[IO](Method.GET, uri"/business/businesses/address/details/user_id_2")

    for {
      response <- controller.orNotFound.run(request)
      body <- response.as[ErrorResponse]
    } yield expect.all(
      response.status == BadRequest,
      body == ErrorResponse("error", "error codes")
    )
  }
}
