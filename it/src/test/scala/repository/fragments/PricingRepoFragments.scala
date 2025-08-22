package repository.fragments

import doobie.implicits.*
import doobie.util.fragment

object PricingPlanRepoFragments {

  val dropPricingPlanTable: fragment.Fragment =
    sql"DROP TABLE IF EXISTS pricing_plans CASCADE"

  val resetPricingPlanTable: fragment.Fragment =
    sql"TRUNCATE TABLE pricing_plans RESTART IDENTITY"

  val createPricingPlanTable: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS pricing_plans (
        id BIGSERIAL PRIMARY KEY,
        plan_id VARCHAR(100) UNIQUE NOT NULL,
        name VARCHAR(100) NOT NULL,
        description TEXT,
        stripe_price_id VARCHAR(100),
        features JSONB DEFAULT '{}'::jsonb,
        price NUMERIC NOT NULL DEFAULT 0.00,
        interval VARCHAR(20) DEFAULT 'month',
        user_type VARCHAR(50) CHECK (user_type IN ('Client', 'Dev', 'NoUserType', 'UnknownUserType')),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    """

  val insertPricingPlanData: fragment.Fragment =
    sql"""
      INSERT INTO pricing_plans (
        plan_id,
        name,
        description,
        stripe_price_id,
        features,
        price,
        interval,
        user_type,
        created_at
      ) VALUES
        (
          'PLAN001',
          'ClientFree',
          'planDescription123',
          'stripe_price_id_001',
          jsonb_build_object(
            'maxActiveQuests', 2,
            'devPool', 'auto',
            'estimations', true,
            'canCustomizeLevelThresholds', false,
            'boostQuests', false
          )::jsonb,
          0.00,
          'month',
          'Client',
          '2025-01-02 00:00:00'
        ),
        (
          'PLAN002',
          'ClientStarter',
          'planDescription123',
          'stripe_price_id_002',
          jsonb_build_object(
            'maxActiveQuests', 5,
            'devPool', 'invite',
            'estimations', true,
            'canCustomizeLevelThresholds', false,
            'boostQuests', false
          )::jsonb,
          30.00,
          'month',
          'Client',
          '2025-01-02 00:00:00'
        ),
        (
          'PLAN003',
          'ClientGrowth',
          'planDescription123',
          'stripe_price_id_003',
          jsonb_build_object(
            'maxActiveQuests', 20,
            'devPool', 'invite',
            'estimations', true,
            'canCustomizeLevelThresholds', true,
            'boostQuests', true
          )::jsonb,
          60.00,
          'month',
          'Client',
          '2025-01-02 00:00:00'
        ),
        (
          'PLAN004',
          'ClientScale',
          'planDescription123',
          'stripe_price_id_004',
          jsonb_build_object(
            'maxActiveQuests', 999999999,  -- effectively "Unlimited"
            'devPool', 'invite',
            'estimations', true,
            'canCustomizeLevelThresholds', true,
            'boostQuests', true
          )::jsonb,
          80.00,
          'month',
          'Client',
          '2025-01-02 00:00:00'
        ),
        (
          'PLAN005',
          'DevFree',
          'planDescription123',
          'stripe_price_id_005',
          jsonb_build_object(
            'maxActiveQuests', 1,
            'showOnLeaderBoard', false,
            'communicateWithClient', false
          )::jsonb,
          0.00,
          'month',
          'Dev',
          '2025-01-02 00:00:00'
        ),
        (
          'PLAN006',
          'DevFreelancer',
          'planDescription123',
          'stripe_price_id_006',
          jsonb_build_object(
            'maxActiveQuests', 5,
            'showOnLeaderBoard', true,
            'communicateWithClient', true
          )::jsonb,
          20.00,
          'month',
          'Dev',
          '2025-01-02 00:00:00'
        ),
        (
          'PLAN007',
          'DevPro',
          'planDescription123',
          'stripe_price_id_007',
          jsonb_build_object(
            'maxActiveQuests', 10,
            'showOnLeaderBoard', true,
            'communicateWithClient', true
          )::jsonb,
          40.00,
          'month',
          'Dev',
          '2025-01-02 00:00:00'
        );
    """

}
