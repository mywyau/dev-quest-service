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

trait LanguageDataServiceAlgebra[F[_]] {

  def getLanguageData(devId: String, language: Language): F[Option[LanguageData]]

}

class LanguageDataServiceImpl[F[_] : Concurrent : Monad : Logger](
  languageRepo: LanguageRepositoryAlgebra[F]
) extends LanguageDataServiceAlgebra[F] {

  override def getLanguageData(devId: String, language: Language): F[Option[LanguageData]] =
    languageRepo.getLanguage(devId, language).flatMap {
      case Some(langauge) =>
        Logger[F].info(s"[LanguageDataService] Found $language language data for user with devId: $devId") *>
          Concurrent[F].pure(Some(langauge))
      case None =>
        Logger[F].info(s"[LanguageDataService] No $language language data found for user with devId: $devId") *>
          Concurrent[F].pure(None)
    }

}

object LanguageDataService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](languageRepo: LanguageRepositoryAlgebra[F]): LanguageDataServiceAlgebra[F] =
    new LanguageDataServiceImpl[F](languageRepo)
}
