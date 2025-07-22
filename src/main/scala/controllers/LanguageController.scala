package controllers

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
import models.languages.*
import models.languages.LanguageData
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.LanguageServiceAlgebra

trait LanguageControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class LanguageControllerImpl[F[_] : Async : Concurrent : Logger](
  languageService: LanguageServiceAlgebra[F]
) extends Http4sDsl[F]
    with LanguageControllerAlgebra[F] {

  object PageParam extends OptionalQueryParamDecoderMatcher[Int]("page")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "language" / "health" =>
      Logger[F].debug(s"[LanguageController] GET - Health check for backend LanguageController") *>
        Ok(GetResponse("/dev-quest-service/language/health", "I am alive - LanguageController").asJson)

    case req @ GET -> Root / "hiscore" / "language" / "count" / language =>
      Logger[F].debug(s"[LanguageController] GET - Trying to get count for languages") *>
        languageService.countForLanguage(Language.fromString(language.capitalize)).flatMap { numberOfDevs =>
          Logger[F].debug(s"[LanguageController] GET - Total number of devs with language hiscore data: ${numberOfDevs} for language: $language") *>
            Ok(HiscoreCount(numberOfDevs).asJson)
        }

    // TODO: change this to return a list of paginated languages
    case req @ GET -> Root / "language" / language / devId =>
      Logger[F].debug(s"[LanguageController] GET - Trying to get $language language data for for userId $devId") *>
        languageService.getLanguage(devId, Language.fromString(language)).flatMap {
          case None =>
            BadRequest(ErrorResponse("NO_LANGUAGE_DATA", s"No language data found for $language").asJson)
          case Some(questingLanguageData) =>
            Ok(questingLanguageData.asJson)
        }

    case GET -> Root / "hiscore" / "language" / language :? PageParam(maybePage) +& LimitParam(maybeLimit) =>
      val page = maybePage.getOrElse(1)
      val limit = maybeLimit.getOrElse(50)
      val offset = (page - 1) * limit

      Logger[F].debug(s"[LanguageController] GET - Paginated hiscores for language: $language (offset=$offset, limit=$limit)") *>
        languageService.getPaginatedLanguageData(Language.fromString(language.capitalize), offset, limit).flatMap { case data =>
          Ok(data.asJson)
        }
  }
}

object LanguageController {
  def apply[F[_] : Async : Concurrent](languageService: LanguageServiceAlgebra[F])(implicit logger: Logger[F]): LanguageControllerAlgebra[F] =
    new LanguageControllerImpl[F](languageService)
}
