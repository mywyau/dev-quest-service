package controllers

import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.responses.*
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

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "hiscore" / "health" =>
      Logger[F].debug(s"[HiscoreController] GET - Health check for backend HiscoreController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - HiscoreController").asJson)

// http://localhost:8080/dev-quest-service/hiscore/total/level'
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
