package controllers

import cache.SessionCacheAlgebra
import cats.effect.{IO, Ref}
import controllers.ControllerSpecBase
import controllers.QuestController
import controllers.QuestControllerConstants.*
import models.auth.UserSession
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.Method.*
import org.http4s.Status.Ok
import services.QuestServiceAlgebra
import weaver.SimpleIOSuite
import mocks.MockSessionCache

object QuestControllerSpec extends SimpleIOSuite with ControllerSpecBase {

  def createQuestController(
    questService: QuestServiceAlgebra[IO],
    sessionCache: SessionCacheAlgebra[IO]
  ): HttpRoutes[IO] =
    QuestController[IO](questService, sessionCache).routes

  test("GET - /quest/USER001/QUEST001 should return 200 when quest is retrieved successfully") {

    val sessionToken = "test-session-token"
    val fakeUserSession = UserSession(
      userId      = "USER001",
      cookieValue = sessionToken,
      email       = "fakeEmail@gmail.com",
      userType    = "Dev"
    )
    val mockQuestService = new MockQuestService(Map("QUEST001" -> sampleQuest1))
    val request = Request[IO](Method.GET, uri"/quest/USER001/QUEST001")

    for {
      // create an in-memory session cache and pre-populate it
      sessionCache <- MockSessionCache.make[IO]
      _            <- sessionCache.storeSession("USER001", Some(fakeUserSession))

      controller   = createQuestController(mockQuestService, sessionCache)
      response    <- controller.orNotFound.run(
                       request.addCookie("auth_session", sessionToken)
                     )
    } yield expect(response.status == Ok)
  }
}
