# Config Adapter Flowable Orchestrator

Flowable-based saga orchestrator for multi-adapter provisioning workflows (Dataset lifecycle: FROST, APISIX, NiFi/Redpanda). Drop-in replacement for the custom `config-adapter-orchestrator`.

## How it works

Flowable Engine runs **embedded** in the config-adapter JVM — no extra server or container needed. Saga workflows are defined as BPMN processes. Each saga step calls an existing `SagaCommandHandler` (FROST, APISIX, Redpanda) via a JavaDelegate bridge. State is persisted in PostgreSQL (Flowable's built-in tables with `ACT_` prefix).

```
Kafka Trigger → FlowableTriggerConsumer → Flowable Engine (in-process)
                                             ├─ FROST handler (REST)
                                             ├─ APISIX handler (REST)
                                             └─ Redpanda handler (REST)
                                          → FlowableResultPublisher → Kafka Result
```

The backend does not need any changes — same trigger topic, same result topic, same message format.

## Two Approaches (for comparison)

The module implements the same workflows in two ways:

| Approach | Description | Files |
|----------|-------------|-------|
| **BPMN XML** (default) | Standard BPMN 2.0 `.bpmn` files | `src/main/resources/processes/` |
| **Java Coded** | Programmatic `BpmnModel` builders | `src/main/java/.../coded/` |

Both use the same delegates, same handlers, same Kafka integration. An equivalence test proves identical behavior.

## Configuration

### Environment Variables

All properties support automatic environment variable override (dots → underscores, uppercase):

| Property | Environment Variable | Required | Default | Description |
|----------|---------------------|----------|---------|-------------|
| `flowable.jdbc.url` | `FLOWABLE_JDBC_URL` | **Yes** | — | PostgreSQL JDBC URL |
| `flowable.jdbc.username` | `FLOWABLE_JDBC_USERNAME` | **Yes** | — | Database username |
| `flowable.jdbc.password` | `FLOWABLE_JDBC_PASSWORD` | **Yes** | — | Database password |
| `flowable.approach` | `FLOWABLE_APPROACH` | No | `bpmn` | `bpmn` (XML files) or `coded` (Java builders) |
| `flowable.kafka.group.id` | `FLOWABLE_KAFKA_GROUP_ID` | No | `config-adapter-flowable-group` | Kafka consumer group |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Shared with other adapters |

**Important:** The JDBC properties have **no defaults** — the application fails fast if the database is not configured. This prevents accidental connections to wrong databases.

### Local Development Example

The local `docker-compose.yml` provisions a dedicated `flowable` database and user via `docker/postgres/init-flowable.sql` on first start.

```properties
# application.properties (local dev — matches the docker-compose setup)
flowable.jdbc.url=jdbc:postgresql://localhost:5433/flowable
flowable.jdbc.username=flowable
flowable.jdbc.password=flowable
```

Or via environment variables:

```bash
export FLOWABLE_JDBC_URL=jdbc:postgresql://localhost:5433/flowable
export FLOWABLE_JDBC_USERNAME=flowable
export FLOWABLE_JDBC_PASSWORD=flowable
```

### Database

Flowable runs against a dedicated `flowable` database, separate from the Keycloak database that shares the same PostgreSQL container. It creates ~36 tables with the `ACT_` prefix (e.g., `ACT_RE_DEPLOYMENT`, `ACT_RU_EXECUTION`, `ACT_HI_ACTINST`). Schema is auto-created on first startup. Duplicate process deployments are filtered — restarting the application does not create new versions unless the BPMN files changed.

If the PostgreSQL volume already exists from before this split, recreate it: `docker compose down -v && docker compose up -d`.

## Saga Workflows

### Dataset Create (FROST → APISIX → Redpanda)
- Sequential execution, conditional Redpanda step (skipped if no pipelines)
- On failure: reverse-order compensation (DELETE operations)

### Dataset Update (FROST → APISIX → Redpanda)
- Same structure as Create, with UPDATE/RESTORE operations

### Dataset Delete (Redpanda → APISIX → FROST)
- Reverse order, best-effort: continues on failure, no compensation

## Architecture

The module is organized into three top-level packages:

- `common` — engine bootstrap, infrastructure factories, saga handler registry, JavaDelegate base classes, and the Kafka trigger/result bridge.
- `bpmn` — deployment of the XML-based BPMN process definitions from `src/main/resources/processes/`.
- `coded` — deployment of the programmatically built equivalents (`BpmnModel`-builder).

Both `bpmn` and `coded` produce equivalent process definitions and share the delegates in `common`. An equivalence test enforces this.

### Key Design Decisions

- **No Spring Boot required** — Flowable runs standalone with programmatic `ProcessEngineConfiguration`
- **Handlers reused** — Existing `SagaCommandHandler` implementations (FROST, APISIX, Redpanda) called via JavaDelegate wrappers
- **In-process execution** — No Kafka round-trip per step (unlike custom orchestrator). Only trigger and result go through Kafka.
- **Async disabled for tests** — Test engines run synchronously for deterministic testing. Production engines use async execution for crash recovery.

## Running Tests

```bash
# Unit + process tests (H2 in-memory, no Docker needed)
mvn test -pl config-adapter-flowable

# Integration tests (requires Docker for Testcontainers)
mvn verify -pl config-adapter-flowable

# Single test class
mvn test -pl config-adapter-flowable -Dtest=DatasetCreateBpmnTest
```

## Viewing BPMN Diagrams

The `.bpmn` files in `src/main/resources/processes/` can be viewed with:

- **Camunda Modeler** (Desktop, MIT) — best option, auto-generates layout. Download: https://camunda.com/download/modeler/
- **bpmn.io** (Browser) — requires DI section in the XML (Camunda Modeler adds it on save)
