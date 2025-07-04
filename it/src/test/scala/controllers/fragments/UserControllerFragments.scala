package controllers.fragments

import doobie.implicits.*
import doobie.util.fragment

object UserDataControllerFragments {

  val resetUserDataTable: fragment.Fragment =
    sql"TRUNCATE TABLE users RESTART IDENTITY"

  val createUserDataTable: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS users (
        user_id VARCHAR(255) PRIMARY KEY,
        email VARCHAR(255) NOT NULL,
        username VARCHAR(255) NOT NULL,
        first_name VARCHAR(255),
        last_name VARCHAR(255),
        user_type VARCHAR(50),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    """

  val insertUserData: fragment.Fragment =
    sql"""
        INSERT INTO users (
          user_id,
          email,
          username,
          first_name,
          last_name,
          user_type,
          created_at,
          updated_at
        ) VALUES
          ('USER001', 'bob_smith@gmail.com', 'goku', 'Bob', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER002', 'dylan_smith@gmail.com', 'goku', 'Dylan', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-03 09:30:00'),
          ('USER003', 'sam_smith@gmail.com', 'goku', 'Sam', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-01 00:00:00'),
          ('USER004', 'joe_smith@gmail.com', 'goku', 'Joe', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-04 16:45:00'),
          ('USER005', 'kyle_smith@gmail.com', 'goku', 'Kyle', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00'),
          ('USER008', 'otis_smith@gmail.com', 'goku', 'Otis', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00'),
          ('USER009', 'helen_smith@gmail.com', 'goku', 'Helen', 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00');
    """
}
