package controllers

import cache.RedisCache
import cache.RedisCacheAlgebra
import cache.SessionCacheAlgebra
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.Json
import io.circe.syntax.EncoderOps
import models.Completed
import models.Failed
import models.InProgress
import models.NotStarted
import models.Review
import models.Submitted
import models.database.UpdateSuccess
import models.responses.*
import models.skills.*
import models.skills.SkillData
import org.http4s.*
import org.http4s.Challenge
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import services.SkillDataServiceAlgebra

import scala.concurrent.duration.*

trait SkillControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class SkillControllerImpl[F[_] : Async : Concurrent : Logger](
  skillDataService: SkillDataServiceAlgebra[F]
) extends Http4sDsl[F]
    with SkillControllerAlgebra[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "skill" / "health" =>
      Logger[F].info(s"[SkillController] GET - Health check for backend SkillController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - SkillController").asJson)

    // case req @ GET -> Root / "skill" / skill / devId =>
    //   Logger[F].info(s"[SkillController] GET - Trying to get skill data for userId $devId for skill: $skill") *>
    //     skillDataService.getSkillData(devId, Skill.fromString(skill)).flatMap {
    //       case None =>
    //         BadRequest(ErrorResponse("NO_SKILL_DATA", s"No $skill skill data found").asJson)
    //       case Some(skillData) =>
    //         Ok(skillData.asJson)
    //     }

    // TODO: change this to return a list of paginated skills
    case req @ GET -> Root / "hiscore" / "skill" / skill =>
      Logger[F].info(s"[SkillController] GET - Trying to get hiscores skill data for skill: $skill") *>
        skillDataService.getHiscoreSkillData(Skill.fromString(skill.capitalize)).flatMap {
          case Nil =>
            BadRequest(ErrorResponse("NO_HISCORE_SKILL_DATA", s"No hiscore skill data found: $skill").asJson)
          case hiscoreSkillData =>
            Ok(hiscoreSkillData.asJson)
        }
  }
}

object SkillController {
  def apply[F[_] : Async : Concurrent](skillService: SkillDataServiceAlgebra[F])(implicit logger: Logger[F]): SkillControllerAlgebra[F] =
    new SkillControllerImpl[F](skillService)
}
