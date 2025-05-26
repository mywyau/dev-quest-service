package controllers

import cats.effect.kernel.Resource
import cats.effect.IO
import controllers.BaseController
import controllers.ControllerISpecBase
import models.auth.UserSession
import models.cache.CacheUpdateSuccess
import org.http4s.*
import org.http4s.implicits._
import org.http4s.Method.GET
import scala.concurrent.duration._
import shared.HttpClientResource
import shared.SessionCacheResource
import weaver.GlobalRead
import weaver.IOSuite

class SessionCacheISpec(global: GlobalRead) extends IOSuite with ControllerISpecBase {

  type Res = (SessionCacheResource, HttpClientResource)

  // Sample session data for tests
  private val testSession = UserSession(
    userId = "user-1",
    cookieToken = "tok-xyz",
    email = "foo@bar.com",
    userType = "Client"
  )

  private val testSession3 = UserSession(
    userId = "user-3",
    cookieToken = "tok-xyz-123",
    email = "foo3@bar.com",
    userType = "Client"
  )

  private val testSession4 = UserSession(
    userId = "user-4",
    cookieToken = "tok-xyz-124",
    email = "foo4@bar.com",
    userType = "Dev"
  )

  def sharedResource: Resource[IO, Res] =
    for {
      sessionCache <- global.getOrFailR[SessionCacheResource]()
      client <- global.getOrFailR[HttpClientResource]()
    } yield (sessionCache, client)

  test("getSession returns empty for a not present key  - return empty") { (shared, log) =>
    val cache = shared._1.sessionCache
    cache.getSession("no-user").map(result => expect(result == None))
  }

  test("storeSession / getSession should round-trip JSON") { (shared, log) =>
    val cache = shared._1.sessionCache
    for {
      // store the full JSON
      resultJson <- cache.storeSession(testSession.userId, Some(testSession))
      e1 = expect(resultJson.toOption.contains(CacheUpdateSuccess))
      // fetch raw JSON
      rawOpt <- cache.getSession(testSession.userId)
      e2 = expect(rawOpt.exists(_.contains(testSession.cookieToken)))
    } yield e1.and(e2)
  }

  test("lookupSession should decode the stored session") { (shared, log) =>

    val cache = shared._1.sessionCache

    for {
      _ <- cache.storeSession(testSession4.userId, Some(testSession4))
      found <- cache.lookupSession(testSession4.userId)
    } yield expect(found.contains(testSession4))
  }

  test("deleteSession should remove keys") { (shared, log) =>
    val cache = shared._1.sessionCache
    for {
      _ <- cache.storeSession(testSession3.userId, Some(testSession3))
      deleted <- cache.deleteSession(testSession3.userId)
      e1 = expect(deleted > 0)
      afterGet <- cache.getSession(testSession3.userId)
      e2 = expect(afterGet.isEmpty)
      afterLook <- cache.lookupSession(testSession3.cookieToken)
      e3 = expect(afterLook.isEmpty)
    } yield e1.and(e2).and(e3)
  }

  test("storeOnlyCookie writes raw token and lookupSession fails") { (shared, log) =>
    val cache = shared._1.sessionCache
    for {
      _ <- cache.storeOnlyCookie("user-2", "plain-token")
      rawOpt <- cache.getSession("user-2")
      e1 = expect(rawOpt.contains("plain-token"))
      lookup <- cache.lookupSession("plain-token")
      e2 = expect(lookup.isEmpty)
    } yield e1.and(e2)
  }
}
