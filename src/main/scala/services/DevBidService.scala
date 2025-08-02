package services

import cats.data.EitherT
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import configuration.AppConfig
import fs2.Stream
import java.util.UUID
import models.*
import models.database.*
import models.dev_bids.*
import org.typelevel.log4cats.Logger
import repositories.*

trait DevBidServiceAlgebra[F[_]] {

  def countBids(questId: String): F[Int]

  def getBid(questId: String): F[Option[GetDevBid]]

  def getDevBids(questId: String, limit: Int, offset: Int): F[List[GetDevBid]]

  def upsertBid(devId: String, questId: String, request: DevBid): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] 
}

class DevBidServiceImpl[F[_] : Concurrent : NonEmptyParallel : Monad : Logger](
  appConfig: AppConfig,
  userDataRepo: UserDataRepositoryAlgebra[F],
  devBidRepo: DevBidRepositoryAlgebra[F]
) extends DevBidServiceAlgebra[F] {

  override def countBids(questId: String): F[Int] =
    devBidRepo.getBidCount(questId)

  override def getBid(questId: String): F[Option[GetDevBid]] =
    devBidRepo.getBid(questId)

  override def getDevBids(questId: String, limit: Int, offset: Int): F[List[GetDevBid]] =
    devBidRepo.getDevBids(questId, limit, offset)

  override def upsertBid(devId: String, questId: String, request: DevBid): F[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    userDataRepo.findUser(devId).flatMap {
      case Some(userData) =>
        devBidRepo.upsertBid(devId, questId, userData.username, request).flatTap {
          case Valid(_) =>
            Logger[F].debug(s"[DevBidService][upsertBid] Successfully added/updated the dev's bid for quest: $questId")
          case Invalid(errors) =>
            Logger[F].error(s"[DevBidService][upsertBid] Failed to add/update bid for quest ID: $questId. Errors: ${errors.toList.mkString(", ")}")
        }
      case None =>
        Logger[F].error(s"[DevBidService][upsertBid] User not found for dev ID: $devId") *>
          Concurrent[F].pure(NotFoundError.invalidNel)
    }

}

object DevBidService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    appConfig: AppConfig,
    userDataRepo: UserDataRepositoryAlgebra[F],
    devBidRepo: DevBidRepositoryAlgebra[F]
  ): DevBidServiceAlgebra[F] =
    new DevBidServiceImpl[F](appConfig,userDataRepo, devBidRepo)
}
