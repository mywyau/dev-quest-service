package controllers

import cache.RedisCacheAlgebra
import cats.effect.*
import cats.effect.IO
import controllers.ControllerSpecBase
import controllers.QuestController
import controllers.QuestControllerConstants.*
import models.auth.UserSession
import models.responses.ErrorResponse
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.implicits.*
import org.http4s.Method.*
import org.http4s.Status.BadRequest
import org.http4s.Status.Ok
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import services.QuestServiceAlgebra
import weaver.SimpleIOSuite

object QuestControllerSpec extends SimpleIOSuite with ControllerSpecBase {

  def createUserController(
    questService: QuestServiceAlgebra[IO],
    mockRedisCache: RedisCacheAlgebra[IO]
  ): HttpRoutes[IO] =
    QuestController[IO](questService, mockRedisCache).routes

  test("GET - /quest/USER001/QUEST001 should return 200 when quest is retrieved successfully") {

    val sessionToken = "test-session-token"

    val fakeUserSession =
      UserSession(
        userId = "USER001",
        cookieValue = sessionToken,
        email = "fakeEmail@gmail.com",
        userType = "Dev"
      )

    val mockQuestService = new MockQuestService(Map("QUEST001" -> sampleQuest1))
    val request = Request[IO](Method.GET, uri"/quest/USER001/QUEST001")

    for {
      ref <- Ref.of[IO, Map[String, UserSession]](Map(s"auth:session:USER001" -> fakeUserSession))
      mockRedisCache = new MockRedisCache(ref)
      controller = createUserController(mockQuestService, mockRedisCache)
      response <- controller.orNotFound.run(
        request
          .addCookie("auth_session", sessionToken)
      )
    } yield expect(response.status == Status.Ok)
  }

  // test("POST - /quest/create - should return 400 when a user id is not found") {

  //   val mockQuestService = new MockQuestService(Map())

  //   val controller = createUserController(mockQuestService)

  //   val request = Request[IO](Method.GET, uri"/quest/create")

  //   for {
  //     response <- controller.orNotFound.run(request)
  //     body <- response.as[ErrorResponse]
  //   } yield expect.all(
  //     response.status == BadRequest,
  //     body == ErrorResponse("error", "error codes")
  //   )
  // }
}
