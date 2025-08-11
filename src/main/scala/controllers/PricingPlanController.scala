package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.*
import models.database.UpdateSuccess
import models.quests.*
import models.responses.*
import models.work_time.HoursOfWork
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.QuestCRUDServiceAlgebra
import services.QuestStreamingServiceAlgebra

trait PricingPlanControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class PricingPlanControllerImpl[F[_] : Async : Concurrent : Logger](
  sessionCache: SessionCacheAlgebra[F]
) extends Http4sDsl[F]
    with PricingPlanControllerAlgebra[F] {

  implicit val questStatusQueryParamDecoder: QueryParamDecoder[QuestStatus] =
    QueryParamDecoder[String].emap { str =>
      Either
        .catchNonFatal(QuestStatus.fromString(str))
        .leftMap(t => ParseFailure("Invalid status", t.getMessage))
    }

  object StatusParam extends OptionalQueryParamDecoderMatcher[QuestStatus]("status")
  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private def extractSessionToken(req: Request[F]): Option[String] =
    req.cookies
      .find(_.name == "auth_session")
      .map(_.content)

  private def withValidSession(userId: String, token: String)(onValid: F[Response[F]]): F[Response[F]] =
    sessionCache.getSession(userId).flatMap {
      case Some(userSessionJson) if userSessionJson.cookieValue == token =>
        Logger[F].debug("[PricingPlanControllerImpl][withValidSession] Found valid session for userId:") *>
          onValid
      case Some(_) =>
        Logger[F].debug("[PricingPlanControllerImpl][withValidSession] User session does not match requested user session token value from redis.")
        Forbidden("User session does not match requested user session token value from redis.")
      case None =>
        Logger[F].debug("[PricingPlanControllerImpl][withValidSession] Invalid or expired session")
        Forbidden("Invalid or expired session")
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "billing" / "plans" / "health" =>
      Logger[F].debug(s"[PricingPlanControllerImpl] GET - Health check for backend PricingPlanController service") *>
        Ok(GetResponse("/dev-quest-service/health", "I am alive").asJson)

    case req @ GET -> Root / "billing" / "plans" =>
      Logger[F].debug(s"[PricingPlanController] GET - Health check for backend PricingPlanController service") *>
        Ok(GetResponse("/dev-quest-service/health", "I am alive").asJson)

    case req @ POST -> Root / "billing" / "checkout" => ???

    case req @ POST -> Root / "billing" / "portal" / userIdFromRoute => ???

    case req @ GET -> Root / "billing" / "stripe" / "webhook" / userIdFromRoute => ???

    case req @ GET -> Root / "billing" / "me" / "plan"/ userId => ???
    

    case req @ GET -> Root / "billing" / "stream" / "client" / "new" / userIdFromRoute => ???

  }
}

object PricingPlanController {
  def apply[F[_] : Async : Concurrent](
    sessionCache: SessionCacheAlgebra[F]
  )(implicit logger: Logger[F]): PricingPlanControllerAlgebra[F] =
    new PricingPlanControllerImpl[F](sessionCache)
}
