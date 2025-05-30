package controllers.fragments

import doobie.implicits.*
import doobie.util.fragment

object QuestControllerFragments {

  val resetQuestTable: fragment.Fragment =
    sql"TRUNCATE TABLE quests RESTART IDENTITY"

  val createQuestTable: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS quests (
        id BIGSERIAL PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL,
        quest_id VARCHAR(255) NOT NULL,
        title VARCHAR(255) NOT NULL,
        description TEXT NOT NULL,
        status VARCHAR(50) NOT NULL DEFAULT 'Completed',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    """

  val insertQuestData: fragment.Fragment =
    sql"""
        INSERT INTO quests (
          user_id,
          quest_id,
          title,
          description,
          status,
          created_at,
          updated_at
        ) VALUES
          ('USER001', 'QUEST001', 'Implement User Authentication', 'Set up Auth0 integration and secure routes using JWT tokens.', 'InProgress', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER002', 'QUEST002', 'Add Dark Mode Support', 'Implement theme toggling and persist user preference with localStorage.', 'Completed', '2025-01-01 00:00:00', '2025-01-03 09:30:00'),
          ('USER003', 'QUEST003', 'Refactor API Layer', 'Migrate from custom HTTP clients to use http4s and apply middleware.', 'InProgress', '2025-01-01 00:00:00', '2025-01-01 00:00:00'),
          ('USER004', 'QUEST004', 'Improve Test Coverage', 'Add unit and integration tests for payment service using ScalaTest and Mockito.', 'InProgress', '2025-01-01 00:00:00', '2025-01-04 16:45:00'),
          ('USER005', 'QUEST005', 'Optimize Frontend Performance', 'Analyze bundle size and apply code splitting in Nuxt app.', 'Completed', '2025-01-01 00:00:00', '2025-01-05 11:20:00'),
          ('USER007', 'QUEST010', 'Some Quest Title 1', 'Some Quest Description 1', 'InProgress', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER007', 'QUEST011', 'Some Quest Title 2', 'Some Quest Description 2', 'InProgress', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER007', 'QUEST012', 'Some Quest Title 3', 'Some Quest Description 3', 'Completed', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER007', 'QUEST013', 'Some Quest Title 4', 'Some Quest Description 4', 'Completed', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER007', 'QUEST014', 'Some Quest Title 5', 'Some Quest Description 5', 'NotStarted', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER007', 'QUEST015', 'Some Quest Title 6', 'Some Quest Description 6', 'NotStarted', '2025-01-01 00:00:00', '2025-01-02 12:00:00');
      """
}
