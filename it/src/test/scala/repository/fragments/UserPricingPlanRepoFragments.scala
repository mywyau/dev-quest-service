// it/src/test/scala/repository/fragments/UserPricingPlanRepoFragments.scala
package repository.fragments

import doobie.implicits.*
import doobie.util.fragment
import doobie.util.fragment.Fragment

object UserPricingPlanRepoFragments {

  val dropUserPlansTable: fragment.Fragment =
    sql"DROP TABLE IF EXISTS user_plans CASCADE"

  val resetUserPlans: fragment.Fragment =
    sql"TRUNCATE TABLE user_plans RESTART IDENTITY"

  val resetPricingPlanTables: fragment.Fragment =
    sql"TRUNCATE TABLE user_plans, pricing_plans RESTART IDENTITY CASCADE"

  val createUserPlans: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS user_plans (
        id BIGSERIAL PRIMARY KEY,
        user_id VARCHAR(100) UNIQUE NOT NULL,
        plan_id VARCHAR(100) NOT NULL,
        stripe_subscription_id VARCHAR(255),
        stripe_customer_id VARCHAR(255),
        status VARCHAR(50) NOT NULL,
        started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        current_period_end TIMESTAMPTZ,
        cancel_at_period_end BOOLEAN DEFAULT FALSE,
        last_synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_plan FOREIGN KEY (plan_id) REFERENCES pricing_plans(plan_id) ON DELETE RESTRICT
      );
    """

  // Seed a couple of rows bound to the pricing_plans that you already insert in PricingPlanRepoFragments.insertPricingPlanData
  val seedUserPlans: fragment.Fragment =
    sql"""
      INSERT INTO user_plans
        (user_id, plan_id, stripe_subscription_id, stripe_customer_id, status, started_at, current_period_end, cancel_at_period_end)
      VALUES
        ('u1', 'PLAN002', NULL, 'cus_u1', 'Active',   '2025-02-01 00:00:00', NULL, false),
        ('u2', 'PLAN006', NULL, 'cus_u2', 'Active',   '2025-02-02 00:00:00', NULL, false),
        -- two different users share a customer id to test ORDER BY started_at
        ('old_user', 'PLAN001', NULL, 'cus_dup', 'Active', '2025-01-01 00:00:00', NULL, false),
        ('new_user', 'PLAN003', NULL, 'cus_dup', 'Active', '2025-03-01 00:00:00', NULL, false);
    """
}
