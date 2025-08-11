package repositories

import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all.*
import cats.Monad
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.circe.jsonb.implicits._ // now available
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor
import fs2.Stream
import java.sql.Timestamp
import java.time.LocalDateTime
import models.database.*
import models.pricing.*
import org.typelevel.log4cats.Logger
import services.LevelServiceAlgebra

trait PricingPlanRepositoryAlgebra[F[_]] {

  def listActive: F[List[PricingPlanRow]]

  def byPlanId(id: String): F[Option[PricingPlanRow]]

  def byStripePriceId(priceId: String): F[Option[PricingPlanRow]]

}

class PricingPlanRepositoryImpl[F[_] : Concurrent : Monad : Logger](
  transactor: Transactor[F]
) extends PricingPlanRepositoryAlgebra[F] {

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  override def listActive: F[List[PricingPlanRow]] = {

    val findQuery: F[List[PricingPlanRow]] =
      sql"""
         SELECT 
            plan_id,
            name,
            description,
            price,
            interval,
            is_active,
            stripe_price_id,
            features,
            sort_order,
            created_at
         FROM plans
         WHERE is_active = TRUE
         ORDER BY sort_order
         """
        .query[PricingPlanRow]
        .to[List]
        .transact(transactor)

    findQuery
  }

  override def byPlanId(planId: String): F[Option[PricingPlanRow]] = {

    val findQuery: F[Option[PricingPlanRow]] =
      sql"""
         SELECT 
            plan_id,
            name,
            description,
            price,
            interval,
            is_active,
            stripe_price_id,
            features,
            sort_order,
            created_at
         FROM plans
         WHERE plan_id = $planId
         ORDER BY sort_order
      """
        .query[PricingPlanRow]
        .option
        .transact(transactor)

    findQuery
  }

  override def byStripePriceId(priceId: String): F[Option[PricingPlanRow]] = {

    val findQuery: F[Option[PricingPlanRow]] =
      sql"""
         SELECT 
            plan_id,
            name,
            description,
            price,
            interval,
            is_active,
            stripe_price_id,
            features,
            sort_order,
            created_at
         FROM plans
         WHERE stripe_price_id = $priceId
         ORDER BY sort_order
      """
        .query[PricingPlanRow]
        .option
        .transact(transactor)

    findQuery
  }
}

object PricingPlanRepository {
  def apply[F[_] : Concurrent : Monad : Logger](
    transactor: Transactor[F]
  ): PricingPlanRepositoryAlgebra[F] =
    new PricingPlanRepositoryImpl[F](transactor)
}
