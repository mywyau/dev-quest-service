package repository.fragments

import doobie.implicits.*
import doobie.util.fragment.Fragment

object RewardRepoFragments {

  val resetRewardTable: Fragment =
    sql"TRUNCATE TABLE reward RESTART IDENTITY"

  val createRewardTable: Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS reward (
        id BIGSERIAL PRIMARY KEY,
        quest_id VARCHAR(255) NOT NULL,
        client_id VARCHAR(255) NOT NULL, 
        dev_id VARCHAR(255),
        time_reward_value NUMERIC,
        completion_reward_value NUMERIC,
        paid VARCHAR(50) DEFAULT 'NotPaid',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    """

  val insertRewardData: Fragment =
    sql"""
      INSERT INTO reward (
        quest_id,
        client_id,
        dev_id,
        time_reward_value,
        completion_reward_value,
        paid,
        created_at,
        updated_at
      ) VALUES
        ('QUEST001', 'CLIENT001', 'DEV001', 10.5, 100.0, 'NotPaid', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
        ('QUEST002', 'CLIENT002', 'DEV002', 20.0, 200.0, 'NotPaid', '2025-01-01 00:00:00', '2025-01-03 09:30:00'),
        ('QUEST003', 'CLIENT003', 'DEV003', 10.0, 150.0, 'Paid',    '2025-01-01 00:00:00', '2025-01-01 00:00:00'),
        ('QUEST004', 'CLIENT004', 'DEV004', 30.0, 250.0, 'NotPaid', '2025-01-01 00:00:00', '2025-01-04 16:45:00'),
        ('QUEST005', 'CLIENT005', 'DEV005', 20.5, 300.0, 'NotPaid', '2025-01-01 00:00:00', '2025-01-05 11:20:00');
    """
}
