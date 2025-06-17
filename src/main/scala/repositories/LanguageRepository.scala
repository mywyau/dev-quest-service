package repositories

import cats.Monad
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import models.database.*
import models.database.ConstraintViolation
import models.database.CreateSuccess
import models.database.DataTooLongError
import models.database.DatabaseConnectionError
import models.database.DatabaseError
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.database.DeleteSuccess
import models.database.ForeignKeyViolationError
import models.database.NotFoundError
import models.database.SqlExecutionError
import models.database.UnexpectedResultError
import models.database.UnknownError
import models.database.UpdateSuccess
import models.languages.*
import org.typelevel.log4cats.Logger

import java.sql.Timestamp
import java.time.LocalDateTime

trait LanguageRepositoryAlgebra[F[_]] {

  def getLanguage(devId: String, language: Language): F[Option[LanguageData]]

}

class LanguageRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends LanguageRepositoryAlgebra[F] {

  implicit val languageMeta: Meta[Language] = Meta[String].timap(Language.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def getLanguage(devId: String, language: Language): F[Option[LanguageData]] = {
    val findQuery: F[Option[LanguageData]] =
      sql"""
        SELECT 
          dev_id,
          language,
          level,
          xp
        FROM langauges
        WHERE dev_id = $devId AND language = $language
      """
        .query[LanguageData]
        .option
        .transact(transactor)

    findQuery
  }
}

object LanguageRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): LanguageRepositoryAlgebra[F] =
    new LanguageRepositoryImpl[F](transactor)
}
