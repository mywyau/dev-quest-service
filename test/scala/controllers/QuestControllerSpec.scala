package controllers

import cache.RedisCacheAlgebra
import cats.effect.*
import cats.effect.IO
import controllers.ControllerSpecBase
import controllers.QuestController
import controllers.QuestControllerConstants.*
import models.responses.ErrorResponse
import org.http4s.*
import org.http4s.Method.*
import org.http4s.Status.BadRequest
import org.http4s.Status.Ok
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import services.QuestServiceAlgebra
import weaver.SimpleIOSuite

object QuestControllerSpec extends SimpleIOSuite with ControllerSpecBase {

  def createUserController(
    questService: QuestServiceAlgebra[IO],
    mockRedisCache: RedisCacheAlgebra[IO]
  ): HttpRoutes[IO] =
    QuestController[IO](questService, mockRedisCache).routes

  test("GET - /quest/USER001/questId should return 200 when quest is retrieved successfully") {

    val sessionToken = "test-session-token"
    val mockQuestService = new MockQuestService(Map("questId1" -> sampleQuest1))
    val request = Request[IO](Method.GET, uri"/quest/USER001/questId1")

    for {
      ref <- Ref.of[IO, Map[String, String]](Map(s"auth:session:USER001" -> sessionToken))
      mockRedisCache = new MockRedisCache(ref)
      controller = createUserController(mockQuestService, mockRedisCache)
      response <- controller.orNotFound.run(
        request.withHeaders(Header.Raw(ci"Authorization", s"Bearer $sessionToken"))
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
