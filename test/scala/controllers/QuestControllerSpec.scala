package controllers

import cats.effect.IO
import controllers.ControllerSpecBase
import controllers.QuestController
import controllers.QuestControllerConstants.*
import models.responses.ErrorResponse
import org.http4s.*
import org.http4s.Status.BadRequest
import org.http4s.Status.Ok
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.implicits.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import services.QuestServiceAlgebra
import weaver.SimpleIOSuite


object QuestControllerSpec extends SimpleIOSuite with ControllerSpecBase {

  def createUserController(businessAddressService: QuestServiceAlgebra[IO]): HttpRoutes[IO] =
    QuestController[IO](businessAddressService).routes

  test("GET - /quest should return 200 when quest is retrieved successfully") {

    val mockQuestService = new MockQuestService(Map("questId1" -> sampleQuest1))

    val controller = createUserController(mockQuestService)

    val request = Request[IO](Method.GET, uri"/quest/questId1")

    for {
      response <- controller.orNotFound.run(request)
    } yield expect(response.status == Ok)
  }

  test("POST - /quest/create - should return 400 when a user id is not found") {

    val mockQuestService = new MockQuestService(Map())

    val controller = createUserController(mockQuestService)

    val request = Request[IO](Method.GET, uri"/quest/create")

    for {
      response <- controller.orNotFound.run(request)
      body <- response.as[ErrorResponse]
    } yield expect.all(
      response.status == BadRequest,
      body == ErrorResponse("error", "error codes")
    )
  }
}
