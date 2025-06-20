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
import models.database.UpdateSuccess
import models.responses.*
import models.skills.*
import models.Completed
import models.Failed
import models.InProgress
import models.NotStarted
import models.Review
import models.Submitted
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.ProfileServiceAlgebra

trait ProfileControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class ProfileControllerImpl[F[_] : Async : Concurrent : Logger](
  profileService: ProfileServiceAlgebra[F]
) extends Http4sDsl[F]
    with ProfileControllerAlgebra[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "profile" / "health" =>
      Logger[F].info(s"[ProfileController] GET - Health check for backend ProfileController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - ProfileController").asJson)

    case req @ GET -> Root / "profile" / "skill" / "language" / "data" / devId =>
      Logger[F].info(s"[ProfileController] GET - Trying to get skill data for userId $devId") *>
        profileService.getSkillAndLanguageData(devId).flatMap {
          case Nil =>
            BadRequest(ErrorResponse("NO_PROFILE_SKILL_OR_LANGUAGE_DATA", s"No profile data found").asJson)
          case profileData =>
            Logger[F].info(s"[ProfileController] GET - Successfully retrieved skill & language data for userId $devId, ${profileData.asJson}") *>
            Ok(profileData.asJson)
        }
  }
}

object ProfileController {
  def apply[F[_] : Async : Concurrent](skillService: ProfileServiceAlgebra[F])(implicit logger: Logger[F]): ProfileControllerAlgebra[F] =
    new ProfileControllerImpl[F](skillService)
}
