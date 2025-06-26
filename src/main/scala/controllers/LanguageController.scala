package controllers

import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.Json
import io.circe.syntax.EncoderOps
import models.database.UpdateSuccess
import models.languages.*
import models.languages.LanguageData
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import services.LanguageServiceAlgebra

import scala.concurrent.duration.*

trait LanguageControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class LanguageControllerImpl[F[_] : Async : Concurrent : Logger](
  languageService: LanguageServiceAlgebra[F]
) extends Http4sDsl[F]
    with LanguageControllerAlgebra[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "language" / "health" =>
      Logger[F].debug(s"[LanguageController] GET - Health check for backend LanguageController") *>
        Ok(GetResponse("/dev-quest-service/language/health", "I am alive - LanguageController").asJson)

    // TODO: change this to return a list of paginated languages
    case req @ GET -> Root / "language" / language / devId =>
      Logger[F].debug(s"[LanguageController] GET - Trying to get $language language data for for userId $devId") *>
        languageService.getLanguage(devId, Language.fromString(language)).flatMap {
          case None =>
            BadRequest(ErrorResponse("NO_LANGUAGE_DATA", s"No language data found for $language").asJson)
          case Some(questingLanguageData) =>
            Ok(questingLanguageData.asJson)
        }

    // TODO: change this to return a list of paginated languages
    case req @ GET -> Root / "hiscore" / "language" / language =>
      Logger[F].debug(s"[LanguageController] GET - Trying to get hiscores language data for language: $language") *>
        languageService.getHiscoreLanguage(Language.fromString(language.capitalize)).flatMap {
          case Nil =>
            BadRequest(ErrorResponse("NO_HISCORE_LANGUAGE_DATA", s"No hiscore language data found: $language").asJson)
          case hiscoreLanguageData =>
            Ok(hiscoreLanguageData.asJson)
        }
  }
}

object LanguageController {
  def apply[F[_] : Async : Concurrent](languageService: LanguageServiceAlgebra[F])(implicit logger: Logger[F]): LanguageControllerAlgebra[F] =
    new LanguageControllerImpl[F](languageService)
}
