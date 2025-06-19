package services

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import fs2.Stream
import java.util.UUID
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.languages.Language
import models.languages.LanguageData
import models.users.*
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.LanguageRepositoryAlgebra

trait LanguageServiceAlgebra[F[_]] {

  def getLanguage(devId: String, language: Language): F[Option[LanguageData]]

  def getHiscoreLanguage(language: Language): F[List[LanguageData]]
}

class LanguageServiceImpl[F[_] : Concurrent : Monad : Logger](
  languageRepo: LanguageRepositoryAlgebra[F]
) extends LanguageServiceAlgebra[F] {

  override def getLanguage(devId: String, language: Language): F[Option[LanguageData]] =
    languageRepo.getLanguage(devId, language).flatMap {
      case Some(langauge) =>
        Logger[F].info(s"[LanguageService] Found $language language data for user with devId: $devId") *>
          Concurrent[F].pure(Some(langauge))
      case None =>
        Logger[F].info(s"[LanguageService] No $language language data found for user with devId: $devId") *>
          Concurrent[F].pure(None)
    }

  override def getHiscoreLanguage(language: Language): F[List[LanguageData]] =
    languageRepo.getHiscoreLanguageData(language).map(_.sortBy(_.xp)(Ordering[BigDecimal].reverse))

}

object LanguageService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](languageRepo: LanguageRepositoryAlgebra[F]): LanguageServiceAlgebra[F] =
    new LanguageServiceImpl[F](languageRepo)
}
