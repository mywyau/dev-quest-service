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
import models.hiscore.HiscoreCount
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

trait SkillControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class SkillControllerImpl[F[_] : Async : Concurrent : Logger](
  skillDataService: SkillDataServiceAlgebra[F]
) extends Http4sDsl[F]
    with SkillControllerAlgebra[F] {

  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "skill" / "health" =>
      Logger[F].debug(s"[SkillController] GET - Health check for backend SkillController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - SkillController").asJson)

    case req @ GET -> Root / "hiscore" / "skill" / "count" / skill =>
      Logger[F].debug(s"[HiscoreController] GET - Trying to get count for quests with statuses not estimated or open") *>
        skillDataService.countForSkill(Skill.fromString(skill.capitalize)).flatMap { numberOfEntries =>
          Logger[F].debug(s"[HiscoreController] GET - Total number of quests with statuses not estimated or open: ${numberOfEntries}") *>
            Ok(HiscoreCount(numberOfEntries).asJson)
        }

    // TODO: change this to return a list of paginated skills
    case GET -> Root / "hiscore" / "skill" / skill :? PageParam(maybePage) +& LimitParam(maybeLimit) =>
      val page = maybePage.getOrElse(1)
      val limit = maybeLimit.getOrElse(50)
      val offset = (page - 1) * limit

      Logger[F].debug(s"[SkillController] GET - Paginated hiscores for skill: $skill (offset=$offset, limit=$limit)") *>
        skillDataService.getPaginatedSkillData(Skill.fromString(skill.capitalize), offset, limit).flatMap {
          case data =>
            Ok(data.asJson)
        }
  }
}

object SkillController {
  def apply[F[_] : Async : Concurrent](skillService: SkillDataServiceAlgebra[F])(implicit logger: Logger[F]): SkillControllerAlgebra[F] =
    new SkillControllerImpl[F](skillService)
}
