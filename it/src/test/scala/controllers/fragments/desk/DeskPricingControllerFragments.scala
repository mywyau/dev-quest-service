package controllers.fragments.desk

import doobie.implicits.*
import doobie.util.fragment

object DeskPricingControllerFragments {

  val resetDeskPricingsTable: fragment.Fragment =
    sql"TRUNCATE TABLE desk_pricing RESTART IDENTITY"

  val createDeskPricingsTable: fragment.Fragment =
    sql"""
        CREATE TABLE IF NOT EXISTS desk_pricing (
          id SERIAL PRIMARY KEY,
          user_id VARCHAR(255),
          business_id VARCHAR(255),
          office_id VARCHAR(255),
          desk_id VARCHAR(255) UNIQUE,
          price_per_hour DECIMAL(10, 2) CHECK (price_per_hour >= 0),
          price_per_day DECIMAL(10, 2) CHECK (price_per_day >= 0),
          price_per_week DECIMAL(10, 2) CHECK (price_per_week >= 0),
          price_per_month DECIMAL(10, 2) CHECK (price_per_month >= 0),
          price_per_year DECIMAL(10, 2) CHECK (price_per_year >= 0),
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """

  val insertDeskPricings: fragment.Fragment =
    sql"""
      INSERT INTO desk_pricing (
        user_id,
        business_id,
        office_id,
        desk_id,
        price_per_hour,
        price_per_day,
        price_per_week,
        price_per_month,
        price_per_year,
        created_at,
        updated_at
      ) VALUES
        (
          'user001',
          'businessId1',
          'officeId1',
          'deskId1',
          30.00,
          180.00,
          450.00,
          1000.00,
          9000.00,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        ),
        (
          'user002',
          'businessId2',
          'office002',
          'deskId2',
          10.00,
          80.00,
          NULL, 
          1500.00,
          18000.00,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        ),
        (
          'user003',
          'businessId3',
          'office003',
          'deskId3',
          NULL, 
          120.00,
          700.00,
          2500.00,
          NULL, 
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        ),
        (
          'user004',
          'businessId4',
          'office004',
          'deskId4',
          20.00,
          NULL,
          1000.00,
          3000.00,
          36000.00,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        ),
        (
          'user005',
          'businessId5',
          'office005',
          'deskId5',
          NULL,
          NULL,
          NULL,
          NULL,
          50000.00,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        );
    """

}
