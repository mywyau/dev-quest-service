package controllers

import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.hiscore.HiscoreCount
import models.languages.Language
import models.responses.*
import models.skills.Skill
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.LevelServiceAlgebra

trait HiscoreControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class HiscoreControllerImpl[F[_] : Async : Concurrent : Logger](
  levelServiceAlgebra: LevelServiceAlgebra[F]
) extends Http4sDsl[F]
    with HiscoreControllerAlgebra[F] {

  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "hiscore" / "health" =>
      Logger[F].debug(s"[HiscoreController] GET - Health check for backend HiscoreController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - HiscoreController").asJson)

    case req @ GET -> Root / "hiscore" / "total-level" / "count" =>
      Logger[F].debug(s"[HiscoreController][/quest/count/not-estimated/and/open] GET - Trying to get count for quests with statuses not estimated or open") *>
        levelServiceAlgebra.countTotalUsers().flatMap { numberOfEntries =>
          Logger[F].debug(s"[HiscoreController] GET - Total number of quests with statuses not estimated or open: ${numberOfEntries}") *>
            Ok(HiscoreCount(numberOfEntries).asJson)
        }

    // TODO: change this to return a list of paginated skills
    case GET -> Root / "hiscore" / "total-level" :? PageParam(maybePage) +& LimitParam(maybeLimit) =>
      val page = maybePage.getOrElse(1)
      val limit = maybeLimit.getOrElse(50)
      val offset = (page - 1) * limit

      Logger[F].debug(s"[SkillController] GET - Paginated hiscores for total level data (offset=$offset, limit=$limit)") *>
        levelServiceAlgebra.getPaginatedTotalLevelHiscores(offset, limit).flatMap {
          case data => Ok(data.asJson)
        }

    // TODO: change this to return a stream of paginated total level data
    case req @ GET -> Root / "hiscore" / "total" / "level" =>
      Logger[F].debug(s"[HiscoreController] GET - Trying to get all sorted hiscores total level") *>
        levelServiceAlgebra.getTotalLevelHiscores().flatMap {
          case Nil =>
            BadRequest(ErrorResponse("NO_TOTAL_LEVEL_DATA", s"No hiscore total level data found").asJson)
          case totalLevels =>
            Ok(totalLevels.asJson)
        }
  }
}

object HiscoreController {
  def apply[F[_] : Async : Concurrent](
    levelServiceAlgebra: LevelServiceAlgebra[F]
  )(implicit logger: Logger[F]): HiscoreControllerAlgebra[F] =
    new HiscoreControllerImpl[F](levelServiceAlgebra)
}
