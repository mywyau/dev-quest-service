package repository.fragments

import doobie.implicits.*
import doobie.util.fragment

object QuestRepoFragments {

  val resetQuestTable: fragment.Fragment =
    sql"TRUNCATE TABLE quests RESTART IDENTITY"

  val createQuestTable: fragment.Fragment =
    sql"""
      CREATE TABLE IF NOT EXISTS quests (
        id BIGSERIAL PRIMARY KEY,
        client_id VARCHAR(255) NOT NULL,
        quest_id VARCHAR(255) NOT NULL,
        dev_id VARCHAR(255),
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
          client_id,
          quest_id,
          dev_id,
          title,
          description,
          status,
          created_at,
          updated_at
        ) VALUES
          ('USER001', 'QUEST001', 'DEV001', 'Implement User Authentication', 'Set up Auth0 integration and secure routes using JWT tokens.', 'InProgress', '2025-01-01 00:00:00', '2025-01-02 12:00:00'),
          ('USER002', 'QUEST002', 'DEV002', 'Add Dark Mode Support', 'Implement theme toggling and persist user preference with localStorage.', 'Completed', '2025-01-01 00:00:00', '2025-01-03 09:30:00'),
          ('USER003', 'QUEST003', 'DEV003', 'Refactor API Layer', 'Migrate from custom HTTP clients to use http4s and apply middleware.', 'InProgress', '2025-01-01 00:00:00', '2025-01-01 00:00:00'),
          ('USER004', 'QUEST004', 'DEV004', 'Improve Test Coverage', 'Add unit and integration tests for payment service using ScalaTest and Mockito.', 'InProgress', '2025-01-01 00:00:00', '2025-01-04 16:45:00'),
          ('USER005', 'QUEST005', 'DEV005', 'Optimize Frontend Performance', 'Analyze bundle size and apply code splitting in Nuxt app.', 'Completed', '2025-01-01 00:00:00', '2025-01-05 11:20:00');
      """
}
