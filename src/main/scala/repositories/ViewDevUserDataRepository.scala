package repositories

import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import cats.Monad
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import java.sql.Timestamp
import java.time.LocalDateTime
import models.database.*
import models.users.*
import models.UserType
import org.typelevel.log4cats.Logger

trait ViewDevUserDataRepositoryAlgebra[F[_]] {

  def findDevUser(username: String): F[Option[DevUserData]]
}

class ViewDevUserDataRepositoryImpl[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]) extends ViewDevUserDataRepositoryAlgebra[F] {

  implicit val userMeta: Meta[UserType] = Meta[String].timap(UserType.fromString)(_.toString)

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def findDevUser(username: String): F[Option[DevUserData]] = {
    val query =
      sql"""
        SELECT 
          user_id,
          username,
          email,
          mobile,
          first_name,
          last_name
        FROM users
        WHERE username = $username AND user_type = 'Dev'
      """.query[DevUserData]

    query.option.transact(transactor)
  }
}

object ViewDevUserDataRepository {
  def apply[F[_] : Concurrent : Monad : Logger](transactor: Transactor[F]): ViewDevUserDataRepositoryAlgebra[F] =
    new ViewDevUserDataRepositoryImpl[F](transactor)
}
