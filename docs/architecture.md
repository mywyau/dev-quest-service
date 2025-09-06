# Dev Quest Architecture

```mermaid
flowchart TD

  %% ============ Clients ============
  subgraph Clients
    FE[Nuxt Frontend]
    APIUsers[External API Users]
  end

  %% ============ Core Service (Monolith for now) ============
  subgraph DevQuest["Dev Quest Service (http4s • fs2 • Doobie)"]
    HTTP[HTTP Routes / REST]
    ServiceLogic[Domain Services Quests, Estimation, Pricing, Users]
    Cache[(Redis)]
    WriteDB[(Postgres - Write)]
    ReadDB[(Postgres - Read/Replica)]
    Outbox[(Outbox table)]
  end

  %% ============ Kafka ============
  subgraph KafkaBus["Kafka"]
    QC[quest.created.v1]
    EF[estimation.finalized.v1]
    QU[quest.updated.v1]
  end

  %% ============ Consumers / Projections ============
  subgraph Consumers["Consumers (fs2-kafka)"]
    QuestCreatedConsumer["QuestCreatedConsumer"]
    EstimationFinalizedConsumer["EstimationFinalizedConsumer"]
    QuestUpdatedConsumer["QuestUpdatedConsumer"]
    SearchIndexer["SearchIndexer (Quest docs)"]
    Notifier["NotificationConsumer (emails/webhooks)"]
  end

  %% ============ Search ============
  OpenSearch[(OpenSearch - Quest Index)]

  %% ============ Scheduled Job ============
  subgraph Jobs["Scheduled Jobs"]
    EstimationFinalizer["Estimation Finalizer Job\n(fs2 Stream schedule)"]
  end

  %% ---------- Client traffic ----------
  FE -->|HTTP| HTTP
  APIUsers -->|HTTP| HTTP

  %% ---------- Core service flows ----------
  HTTP --> ServiceLogic
  ServiceLogic --> Cache
  ServiceLogic -->|writes| WriteDB
  HTTP -->|reads| ReadDB

  %% ---------- Outbox -> Kafka ----------
  WriteDB --> Outbox
  Outbox -->|producer| QC
  Outbox -->|producer| QU
  ServiceLogic -->|producer| EF

  %% ---------- Kafka -> Consumers ----------
  QC --> QuestCreatedConsumer
  QU --> QuestUpdatedConsumer
  EF --> EstimationFinalizedConsumer

  %% ---------- Projections / Read models ----------
  QuestCreatedConsumer -->|project| ReadDB
  QuestUpdatedConsumer -->|project| ReadDB

  %% ---------- Search indexing ----------
  QuestCreatedConsumer -->|index| OpenSearch
  QuestUpdatedConsumer -->|reindex| OpenSearch
  EstimationFinalizedConsumer -->|partial update| OpenSearch
  SearchIndexer -. optional .-> OpenSearch

  %% ---------- Notifications ----------
  EstimationFinalizedConsumer --> Notifier
  QuestCreatedConsumer --> Notifier

  %% ---------- Scheduled job orchestration ----------
  EstimationFinalizer -->|queries expired| ReadDB
  EstimationFinalizer -->|finalize -> produce EF| KafkaBus
  EstimationFinalizer -->|calls| ServiceLogic
