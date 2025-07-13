package repository.fragments

import doobie.implicits.*
import doobie.util.fragment

object UserRepoFragments {

  val resetUserTable: fragment.Fragment =
    sql"TRUNCATE TABLE users RESTART IDENTITY"

  val createUserTable: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS users (
          id BIGSERIAL PRIMARY KEY,
          user_id VARCHAR(255) UNIQUE,
          username VARCHAR(50) UNIQUE,
          email VARCHAR(255) NOT NULL,
          first_name VARCHAR(255),
          last_name VARCHAR(255),
          user_type VARCHAR(50) CHECK (user_type IN ('Client', 'Dev', 'UnknownUserType')),
          reputation INT DEFAULT 0,              
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    """

  val insertUserData: fragment.Fragment =
    sql"""
        INSERT INTO users (
          user_id,
          username,
          email,
          first_name,
          last_name,
          user_type,
          created_at,
          updated_at
        ) VALUES
          ('USER001', 'cloud', 'bob_smith@gmail.com', 'Bob' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER002', '', 'dylan_smith@gmail.com', NULL, NULL,  'Dev', '2025-01-01 00:00:00', '2025-01-03 09:30:00'),
          ('USER003', 'tidus', 'sam_smith@gmail.com', 'Sam' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-01 00:00:00'),
          ('USER004', 'wakka', 'joe_smith@gmail.com', 'Joe' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-04 16:45:00'),
          ('USER005', 'yuna', 'kyle_smith@gmail.com', 'Kyle' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00');
    """
}
