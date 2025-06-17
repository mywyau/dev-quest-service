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
import models.skills.SkillData
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
import services.SkillDataServiceAlgebra

trait SkillsControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class SkillsControllerImpl[F[_] : Async : Concurrent : Logger](
  skillDataService: SkillDataServiceAlgebra[F]
) extends Http4sDsl[F]
    with SkillsControllerAlgebra[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "skill" / "health" =>
      Logger[F].info(s"[SkillsController] GET - Health check for backend SkillsController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - SkillsController").asJson)

    // TODO: change this to return a list of paginated skills
    case req @ GET -> Root / "skill" / skill / devId =>
      Logger[F].info(s"[SkillsController] GET - Trying to get questing skill data for for userId $devId") *>
        skillDataService.getSkillData(devId, Skill.fromString(skill)).flatMap {
          case None =>
            BadRequest(ErrorResponse("NO_QUEST_SKILL_DATA", "No questing skill data found").asJson)
          case Some(questingSkillData) =>
            Ok(questingSkillData.asJson)
        }
  }
}

object SkillsController {
  def apply[F[_] : Async : Concurrent](skillService: SkillDataServiceAlgebra[F])(implicit logger: Logger[F]): SkillsControllerAlgebra[F] =
    new SkillsControllerImpl[F](skillService)
}
