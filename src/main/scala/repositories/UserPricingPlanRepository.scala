package repositories

import cats.effect.Concurrent
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.TimestampMeta
import doobie.implicits.javatime.*
import doobie.postgres.circe.jsonb.implicits.*
import doobie.util.Get
import doobie.util.Put // <- use Get/Put not Meta for Json
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.Json
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import models.pricing.*
import models.UserType
import org.typelevel.log4cats.Logger

trait UserPricingPlanRepositoryAlgebra[F[_]] {

  def get(userId: String): F[Option[UserPricingPlanView]]

  def upsert(up: UserPlanUpsert): F[UserPricingPlanRow]

  def setStatus(
    userId: String,
    status: String,
    currentPeriodEnd: Option[Instant],
    cancelAtPeriodEnd: Boolean
  ): F[UserPricingPlanRow]

  def findUserIdByStripeCustomerId(customerId: String): F[Option[String]]
}

final class UserPricingPlanRepositoryImpl[F[_] : Concurrent : Logger](
  xa: Transactor[F]
) extends UserPricingPlanRepositoryAlgebra[F] {

  // implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  implicit val userMeta: Meta[UserType] = Meta[String].timap(UserType.fromString)(_.toString)

  implicit val instantMeta: Meta[Instant] = Meta[OffsetDateTime].imap(_.toInstant)(_.atOffset(ZoneOffset.UTC))

  implicit val userPricingPlanStatusMeta: Meta[UserPricingPlanStatus] = Meta[String].timap(UserPricingPlanStatus.fromStripeStatus)(_.toString)

  given Get[PlanFeatures] =
    summon[Get[Json]].temap { json =>
      decode[PlanFeatures](json.noSpaces).left.map(_.getMessage)
    }

  given Put[PlanFeatures] =
    summon[Put[Json]].contramap(_.asJson)

  private def selectUserPricingPlanRow(frWhere: Fragment): Query0[UserPricingPlanRow] =
    (fr"""
      SELECT
        user_id,
        plan_id,
        stripe_subscription_id,
        stripe_customer_id,
        status,
        started_at,
        current_period_end,
        cancel_at_period_end
      FROM user_plans
    """ ++ frWhere).query[UserPricingPlanRow]

  private def selectUserPricingPlanView(frWhere: Fragment): Query0[UserPricingPlanView] =
    (fr"""
    SELECT
      up.user_id,
      up.plan_id,
      up.stripe_subscription_id,
      up.stripe_customer_id,
      up.status,
      up.started_at,
      up.current_period_end,
      up.cancel_at_period_end,

      p.plan_id,
      p.name,
      p.description,
      p.stripe_price_id,
      p.features,
      p.price,
      p.interval,
      p.user_type,
      p.created_at
    FROM user_plans up
    JOIN pricing_plans p ON p.plan_id = up.plan_id
  """ ++ frWhere)
      .query[(UserPricingPlanRow, PricingPlanRow)]
      .map { case (u, p) => UserPricingPlanView(u, p) }

  def upsert(up: UserPlanUpsert): F[UserPricingPlanRow] =
    sql"""
      INSERT INTO user_plans(
        user_id,
        plan_id,
        stripe_subscription_id,
        stripe_customer_id,
        status,
        current_period_end
      )
      VALUES (
              ${up.userId}, ${up.planId}, ${up.stripeSubscriptionId}, ${up.stripeCustomerId},
              ${up.status}, ${up.currentPeriodEnd}
      )
      ON CONFLICT (user_id) DO UPDATE SET
        plan_id               = EXCLUDED.plan_id,
        stripe_subscription_id= EXCLUDED.stripe_subscription_id,
        stripe_customer_id    = EXCLUDED.stripe_customer_id,
        status                = EXCLUDED.status,
        current_period_end    = EXCLUDED.current_period_end,
        last_synced_at        = now()
      RETURNING user_id, plan_id, stripe_subscription_id, stripe_customer_id,
                status, started_at, current_period_end, cancel_at_period_end
    """.query[UserPricingPlanRow].unique.transact(xa)

  def get(userId: String): F[Option[UserPricingPlanView]] =
    selectUserPricingPlanView(fr"WHERE up.user_id = $userId LIMIT 1").option.transact(xa)

  def setStatus(
    userId: String,
    status: String,
    currentPeriodEnd: Option[Instant],
    cancelAtPeriodEnd: Boolean
  ): F[UserPricingPlanRow] =
    sql"""
      UPDATE user_plans
        SET status = $status,
             current_period_end = $currentPeriodEnd,
             cancel_at_period_end = $cancelAtPeriodEnd,
             last_synced_at = now()
        WHERE user_id = $userId
      RETURNING user_id, plan_id, stripe_subscription_id, stripe_customer_id,
                status, started_at, current_period_end, cancel_at_period_end
    """.query[UserPricingPlanRow].unique.transact(xa)

  def findUserIdByStripeCustomerId(customerId: String): F[Option[String]] =
    sql"""
    SELECT user_id
    FROM user_plans
    WHERE stripe_customer_id = $customerId
    ORDER BY started_at DESC
    LIMIT 1
  """.query[String].option.transact(xa)
}

object UserPricingPlanRepository {
  def apply[F[_] : Concurrent : Logger](xa: Transactor[F]): UserPricingPlanRepositoryAlgebra[F] =
    new UserPricingPlanRepositoryImpl[F](xa)
}
