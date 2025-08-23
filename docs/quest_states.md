# Quest States

```mermaid

stateDiagram-v2
    [*] --> Draft
    Draft --> Open: Client publishes
    Draft --> Cancelled: Client cancels

    Open --> Estimating: First estimate arrives (optional)
    Estimating --> Open: Threshold not reached (more estimates)
    Estimating --> EstimationClosed: Threshold reached + countdown elapsed
    Open --> EstimationClosed: Manual close by client

    EstimationClosed --> InProgress: Client assigns dev (or auto-match)
    InProgress --> Review: Dev submits work
    Review --> InProgress: Request changes (optional)
    Review --> Completed: Client approves
    InProgress --> Cancelled: Client cancels (policy‑bound)
    Open --> Cancelled: Client cancels (no work started)

    Completed --> [*]
    Cancelled --> [*]
