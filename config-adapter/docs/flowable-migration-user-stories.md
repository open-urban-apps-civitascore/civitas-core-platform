# User Stories: Flowable Saga Orchestrator Migration

> **Issue:** [#1273](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/work_items/1273)
> **Goal:** Replace custom saga orchestrator with Flowable Engine. Implement the Dataset saga in two approaches (BPMN XML and Java-coded) for comparison. Existing orchestrator stays untouched — the backend does not need any changes.

---

## Story 1: Flowable Engine Integration

**As a** developer,
**I want** a new `config-adapter-flowable` Maven module with an embedded Flowable Engine,
**so that** we have the foundation to run BPMN-based saga workflows within the existing config-adapter JVM.

**Acceptance Criteria:**
- New Maven module exists with Flowable Engine as dependency
- Engine starts with a configurable PostgreSQL DataSource (own schema in existing PG)
- Engine uses async execution so state is persisted between saga steps (crash recovery)
- Unit test confirms the engine can deploy and execute a minimal BPMN process
- Existing modules and tests are not affected

---

## Story 2: Saga Step Delegates

**As a** developer,
**I want** reusable JavaDelegate classes that bridge between Flowable and the existing adapter handlers (FROST, APISIX, RedPanda),
**so that** BPMN processes can call the existing adapter logic without changes to the adapter modules.

**Acceptance Criteria:**
- A forward-execution delegate that reads process variables, calls the appropriate `SagaCommandHandler`, and writes results back to process variables
- A compensation delegate that reads compensation data from process variables and calls the handler's compensation operation
- Inter-step data mapping works correctly (e.g. FROST's `baseUrl` is available as `upstreamUrl` for APISIX)
- On handler failure, the delegate throws a BPMN error to trigger compensation
- Unit tests with mocked handlers verify all behaviors

---

## Story 3: Dataset Saga as BPMN (Approach A)

**As a** developer,
**I want** the three Dataset saga workflows (Create, Update, Delete) defined as BPMN XML files,
**so that** the workflows are visual, standardized (ISO 19510), and can be viewed/edited with BPMN tools.

**Acceptance Criteria:**
- `dataset-create.bpmn20.xml`: FROST → APISIX → RedPanda (conditional), with automatic reverse-order compensation on failure
- `dataset-update.bpmn20.xml`: same structure with update/restore operations
- `dataset-delete.bpmn20.xml`: reverse order (RedPanda → APISIX → FROST), best-effort (continues on failure, no compensation)
- Conditional step: RedPanda is skipped when no data pipelines are present
- Process tests verify: happy path, step failure with compensation, conditional skip, delete best-effort

---

## Story 4: Dataset Saga as Java Code (Approach B)

**As a** developer,
**I want** the same three Dataset saga workflows defined programmatically in Java (using Flowable's BpmnModel API),
**so that** we can compare the Java-coded approach with the BPMN XML approach.

**Acceptance Criteria:**
- Java builders produce functionally identical workflows to the BPMN XML files
- Same delegates, same process variables, same compensation behavior
- Cleanly separated in its own package so it can be removed later without affecting Approach A
- An equivalence test runs the same scenarios against both approaches and confirms identical outcomes
- A configuration property selects which approach is active

---

## Story 5: Kafka Integration

**As a** developer,
**I want** the Flowable orchestrator to consume saga triggers from Kafka and publish results to Kafka,
**so that** it is a drop-in replacement for the existing orchestrator — the backend does not need any changes.

**Acceptance Criteria:**
- Trigger consumer listens on `de.civitascore.dataset.saga.trigger` and starts the correct Flowable process
- Result publisher at the end of each process publishes to `de.civitascore.saga.result` in the exact same format as the current orchestrator
- Uses a different Kafka consumer group ID so it can coexist with the custom orchestrator on the same cluster
- Integration test with Testcontainers (Kafka + PostgreSQL + FROST) verifies end-to-end: trigger in → saga executes → result out

---

## Story 6: Application Wiring

**As an** operator,
**I want** a configuration switch to choose between the custom orchestrator and the Flowable orchestrator,
**so that** we can run either one without code changes and compare them in the same environment.

**Acceptance Criteria:**
- `orchestrator.engine=custom|flowable` selects the orchestrator (default: `custom`)
- `flowable.approach=bpmn|coded` selects BPMN XML or Java-coded approach (default: `bpmn`)
- `flowable.jdbc.url`, `flowable.jdbc.username`, `flowable.jdbc.password` configure the database connection
- All existing tests still pass (`mvn verify` from root)
