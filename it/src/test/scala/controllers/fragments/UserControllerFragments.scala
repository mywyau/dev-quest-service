package controllers.fragments

import doobie.implicits.*
import doobie.util.fragment

object UserDataControllerFragments {

  val resetUserDataTable: fragment.Fragment =
    sql"TRUNCATE TABLE users RESTART IDENTITY"

  val createUserDataTable: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS users (
          id BIGSERIAL PRIMARY KEY,
          user_id VARCHAR(255) UNIQUE,
          username VARCHAR(50) UNIQUE,
          email VARCHAR(255) NOT NULL,
          first_name VARCHAR(255),
          last_name VARCHAR(255),
          user_type VARCHAR(50) CHECK (user_type IN ('Client', 'Dev', 'UnknownUserType')),
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
          ('USER001', 'goku', 'bob_smith@gmail.com', 'Bob' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER002', 'vegeta', 'dylan_smith@gmail.com', 'Dylan' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-03 09:30:00'),
          ('USER003', 'trunks', 'sam_smith@gmail.com', 'Sam' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-01 00:00:00'),
          ('USER004', 'piccolo', 'joe_smith@gmail.com', 'Joe' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-04 16:45:00'),
          ('USER005', 'gohan', 'kyle_smith@gmail.com', 'Kyle' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00'),
          ('USER006', 'bulma', 'bulma@gmail.com', 'Bulma' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00'),
          ('USER008', 'videl', 'videl@gmail.com', 'Videl' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00'),
          ('USER009', 'mr_satan', 'mr_satan@gmail.com', 'Satan' , 'Smith',  'Dev', '2025-01-01 00:00:00', '2025-01-05 11:20:00');
    """
}
