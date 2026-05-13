## Answers to the Framework Decision Questions

### 1. Which workflows are planned?

- We need to support **DataSinks** as well as **DataSources**. Config Adapters are bound to Sources or Sinks.
- **DataPools**: Still open / to be clarified.
- This means at least **3+ workflows** are expected (Dataset, DataSource, DataSink, potentially DataPool).

### 2. Workflow complexity & dependencies

- **Parallel steps**: Not yet.
- **Branching/conditions**: Maybe, but no priority.
- **Nested workflows**: Maybe, but no priority.
- **Adapter matrix**: Does not exist yet.
- **New adapters/backend systems**: Yes, new ones will be added.
- **Summary**: Workflows are currently linear/sequential, but complexity may grow over time.

### 3. Error handling & compensation

- **Consistency**: Best effort — third-party components cannot guarantee transactions.
- **Partial failures**: Complete rollback for now.
- **Manual intervention**: Backend needs to be notified about the state.
- **Critical compensation failures**: N/A.

### 4. Runtime behavior & SLAs

- **Latency**: Not defined yet.
- **Throughput**: Low — source is the user interface, workflows are not frequently triggered.
- **Batch scenarios**: Not yet.
- **UI status tracking**: Desired, but hard to realize in the current architecture.

### 5. Saga state & persistence

- **Retention**: Until completion only.
- **Audit requirements**: OpenTelemetry.
- **Crash recovery**: Yes, Kafka should be sufficient.

### 6. Idempotency & ordering

- **Concurrent access**: First come first win, concurrent access must be transactional/synchronized.
- **Ordering**: Handled via Kafka.
- **Duplicates**: System must behave idempotently.

### 7. Caller interface

- **Trigger source**: Currently the backend. Other triggers may be added in the future.
- **Synchronous API**: Event trigger is sufficient for the moment.
- **Caller feedback**: Saga status. Audit logging of the saga steps is the best case.

### 8. Non-functional constraints

- **Operations**: Should work out-of-the-box.
- **Infrastructure**: Use current infrastructure if possible (Kafka, Docker). Additional database needs discussion.
- **Team expertise**: Java. No ramp-up time planned.
- **Licensing**: Must be Open Source.
- **On-premise**: No cloud — self-hosted only.

### 9. Declarative vs. code-defined workflows

- Declarative would be great, Java is also fine.

### 10. Testing & deployment

- Integration tests are fine.
- Workflow versioning would be good.
