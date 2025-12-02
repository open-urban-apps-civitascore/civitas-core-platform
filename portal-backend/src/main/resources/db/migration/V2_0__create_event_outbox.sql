-- Create outbox event table for Transactional Outbox Pattern
-- This table stores domain events before they are published to Kafka

CREATE TABLE IF NOT EXISTS event_outbox
(
    id             UUID                        NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at    TIMESTAMP WITHOUT TIME ZONE,
    created_by     VARCHAR(255),
    modified_by    VARCHAR(255),

    topic          VARCHAR(255)                NOT NULL,
    payload        TEXT                        NOT NULL,
    aggregate_type VARCHAR(100)                NOT NULL,
    aggregate_id   UUID                        NOT NULL,
    retry_count    INTEGER                     NOT NULL DEFAULT 0,
    status         VARCHAR(20)                 NOT NULL DEFAULT 'PENDING',
    processed_at   TIMESTAMP WITHOUT TIME ZONE,
    last_retry_at  TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT pk_event_outbox PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_pending
    ON event_outbox (status, created_at)
    WHERE status = 'PENDING';

-- Index for debugging failed events
CREATE INDEX idx_outbox_failed
    ON event_outbox (status, retry_count, created_at)
    WHERE status = 'FAILED';

-- Index for event lookup by entity
CREATE INDEX idx_outbox_aggregate
    ON event_outbox (aggregate_type, aggregate_id, created_at DESC);



