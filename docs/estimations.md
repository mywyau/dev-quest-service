# Estimations

```mermaid
sequenceDiagram
    participant Client as Client (Nuxt)
    participant API as Dev Quest Service (HTTP)
    participant DBW as Postgres (Write)
    participant DBR as Postgres (Read / Projections)
    participant K as Kafka
    participant CQC as QuestCreatedConsumer
    participant CEF as EstimationFinalizedConsumer
    participant OS as OpenSearch (Quest Index)
    participant Dev as Developer
    participant Pay as Payments (Stripe/etc)
    participant XP as XP/Ranking

    %% --- Quest creation ---
    Client->>API: POST /quests (title, desc, rewards, tags)
    API->>DBW: Insert quest (status=Draft/PendingReview/Open)
    API->>K: Produce quest.created.v1
    K-->>CQC: quest.created.v1
    CQC->>DBR: Create/Update read model
    CQC->>OS: Index quest doc
    API-->>Client: 201 Created (questId)

    %% --- Estimation window (optional) ---
    Dev->>API: POST /quests/{id}/estimates (score, hours, comment)
    API->>DBW: Insert estimate
    API-->>Dev: 200 OK
    Note over API,DBW: When threshold reached -> start countdown
    API->>DBW: Upsert estimation_close_at

    %% --- Finalization of estimation (scheduled or event-driven) ---
    API->>K: (on finalize) estimation.finalized.v1
    K-->>CEF: estimation.finalized.v1
    CEF->>DBR: Project final rank / status update
    CEF->>OS: Partial reindex (rank/status)
    CEF->>XP: Award Estimating XP to participants

    %% --- Assignment (varies by model) ---
    Client->>API: Assign dev (or auto-match)
    API->>DBW: Update quest assignee, status=InProgress
    API->>K: quest.assigned.v1 (optional)
    API-->>Client: 200 OK

    %% --- Work & Submission ---
    Dev->>API: POST /quests/{id}/submit (artifact/links)
    API->>DBW: Save submission, status=Review
    API->>K: quest.submitted.v1 (optional)

    %% --- Review & Completion ---
    Client->>API: POST /quests/{id}/approve
    API->>DBW: Update status=Completed
    API->>Pay: Capture payment / transfer (escrow -> dev)
    Pay-->>API: Payment success
    API->>K: quest.completed.v1

    %% --- Downstream projections after completion ---
    K-->>CEF: quest.completed.v1 (or dedicated consumer)
    CEF->>DBR: Update read model (completedAt, payouts)
    CEF->>OS: Reindex status=Completed
    CEF->>XP: Award Completion XP to assigned dev
    API-->>Client: 200 OK (Completion confirmed)
