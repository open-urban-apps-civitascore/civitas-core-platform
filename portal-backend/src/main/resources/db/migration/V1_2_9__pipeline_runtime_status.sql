CREATE TABLE pipeline_runtime_status
(
    pipeline_id          UUID         NOT NULL,
    state                VARCHAR(20)  NOT NULL,
    source               VARCHAR(20),
    message              VARCHAR(2000),
    sanitized_stacktrace TEXT,
    occurred_at          TIMESTAMP WITH TIME ZONE,
    correlation_id       UUID,
    last_event_id        UUID,
    CONSTRAINT pk_pipeline_runtime_status PRIMARY KEY (pipeline_id),
    CONSTRAINT fk_pipeline_runtime_status_on_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id)
);
