// repositories/PricingPlanRepository.scala
package repositories

import cats.effect.Concurrent
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.TimestampMeta
import doobie.postgres.circe.jsonb.implicits.given   // bring Get[Json]/Put[Json] into scope
import doobie.util.meta.Meta
import doobie.util.transactor.Transactor

import doobie.util.{ Get, Put }                      // <- use Get/Put not Meta for Json
import io.circe.{ Json }
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.Timestamp
import java.time.LocalDateTime
import models.pricing.*
import org.typelevel.log4cats.Logger
import models.UserType

trait PricingPlanRepositoryAlgebra[F[_]] {

  def listPlans(userType: UserType): F[List[PricingPlanRow]]

  def byPlanId(planId: String): F[Option[PricingPlanRow]]
  
  def byStripePriceId(priceId: String): F[Option[PricingPlanRow]]
}

final class PricingPlanRepositoryImpl[F[_]: Concurrent: Logger](xa: Transactor[F])
  extends PricingPlanRepositoryAlgebra[F] {

  implicit val userMeta: Meta[UserType] = Meta[String].timap(UserType.fromString)(_.toString)

  // timestamp without time zone <-> LocalDateTime
  given Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  // PlanFeatures <-> jsonb (via circe Json)
  given Get[PlanFeatures] =
    summon[Get[Json]].temap { json =>
      decode[PlanFeatures](json.noSpaces).left.map(_.getMessage)
    }

  given Put[PlanFeatures] =
    summon[Put[Json]].contramap(_.asJson)

  private val selectCols: Fragment =
    fr"""
      SELECT
        plan_id,         
        name,            
        description,     
        stripe_price_id, 
        features,        
        price,           
        interval,        
        user_type,       
        created_at       
      FROM pricing_plans
    """

  def listPlans(userType: UserType): F[List[PricingPlanRow]] =
    (selectCols ++ fr"WHERE user_type = ${userType.toString()} ORDER BY price ASC, created_at ASC")
      .query[PricingPlanRow]
      .to[List]
      .transact(xa)

  def byPlanId(planId: String): F[Option[PricingPlanRow]] =
    (selectCols ++ fr"WHERE plan_id = $planId LIMIT 1")
      .query[PricingPlanRow]
      .option
      .transact(xa)

  def byStripePriceId(priceId: String): F[Option[PricingPlanRow]] =
    (selectCols ++ fr"WHERE stripe_price_id = $priceId LIMIT 1")
      .query[PricingPlanRow]
      .option
      .transact(xa)
}

object PricingPlanRepository {
  def apply[F[_]: Concurrent: Logger](xa: Transactor[F]): PricingPlanRepositoryAlgebra[F] =
    new PricingPlanRepositoryImpl[F](xa)
}
