# Config Adapter Flowable Orchestrator

Flowable-based saga orchestrator for multi-adapter provisioning workflows (Dataset lifecycle: FROST, APISIX, NiFi/Redpanda). Drop-in replacement for the custom `config-adapter-orchestrator`.

## How it works

Flowable Engine runs **embedded** in the config-adapter JVM — no extra server or container needed. Saga workflows are defined as BPMN processes. Each saga step calls an existing `SagaCommandHandler` (FROST, APISIX, Redpanda) via a JavaDelegate bridge. State is persisted in PostgreSQL (Flowable's built-in tables with `ACT_` prefix).

```text
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

### Dataset Create (FROST → APISIX → conditional GeoServer → conditional Redpanda)
- Sequential execution
- Conditional GeoServer branch (`hasGeoSink`): `CREATE_WORKSPACE` → `CREATE_DATASTORE` →
  conditional `PROVISION_LAYERS` (`hasLayers`)
- Conditional Redpanda step (`hasPipelines`)
- On failure: reverse-order compensation (DELETE operations); the GeoServer branch is undone by a
  single idempotent `DELETE_WORKSPACE` (recursive)

### Dataset Update (FROST → APISIX → conditional GeoServer → conditional Redpanda)
- Same structure as Create; the GeoServer branch is a single `UPDATE_WORKSPACE` step, compensated by
  `RESTORE_WORKSPACE`

### Dataset Delete (Redpanda → APISIX → conditional GeoServer → FROST)
- Reverse order, best-effort: continues on failure, no compensation
- Conditional GeoServer teardown (`hasGeoSink`): `DELETE_WORKSPACE` (recursive), after the APISIX
  route is removed

### GeoServer branch — conditional and currently dormant

The GeoServer steps run only when the saga trigger carries geo data. Two flags are **derived** from
the trigger payload by `FlowableTriggerConsumer` (never trusted from the payload):

| Flag | Derived when |
|------|--------------|
| `hasGeoSink` | `dataSinks` contains a sink with `dataSinkType == "POSTGIS"` |
| `hasLayers` | `layers` is a non-empty list |

Expected trigger payload shape the GeoServer handler consumes (to be emitted by the backend in a
follow-up — see below):

```jsonc
{
  "dataSinks": [
    { "id": "...", "dataSinkType": "POSTGIS",
      "configuration": { "tableName": "...", "dataStructureVersionId": "..." } }
  ],
  "layers": [
    { "layerName": "...", "nativeName": "...", "crs": "EPSG:4326" }
  ]
}
```

**Dormant today:** the backend's saga trigger (`SagaTrigger` / `DataSetSagaPublisher` in
`portal-backend`) does **not yet emit** `dataSinks` or `layers`, so `hasGeoSink`/`hasLayers` always
derive to `false` and the GeoServer branch is skipped — the existing FROST → APISIX → Redpanda flow
is unchanged. Activating GeoServer end-to-end requires a separate backend change to serialize the
`POSTGIS` `DataSink` and `Layer` entities into the trigger.

**This applies to DELETE too.** The GeoServer teardown (`DELETE_WORKSPACE`) is gated on the same
derived `hasGeoSink`, so the **`DATASET_DELETE` trigger must also carry the `POSTGIS` `dataSinks`**
for the workspace to be removed — symmetric with create/update. A delete trigger without `dataSinks`
skips the teardown (so the workspace would not be removed). Gating it this way (rather than always
deleting) keeps deployments **without** a GeoServer adapter from failing every delete and avoids a
spurious `DELETE …?recurse=true` on non-geo datasets. The end-to-end derivation for delete is
covered by `DatasetDeleteTriggerTest` (realistic trigger through `FlowableTriggerConsumer`).

> Vocabulary note: the GeoServer concept doc uses a `GEO_PERSISTENCE` sink type, but the
> config-adapter targets the `portal-model` vocabulary (`DataSinkType.POSTGIS` + a separate `Layer`
> entity). The concept doc should be reconciled to the `POSTGIS` naming.

### Adapter handlers: required vs optional

The orchestrator only **requires** the handlers that every saga path uses unconditionally — `frost`
and `apisix` — and fails fast at startup if either is missing. The **pipeline** adapter (`redpanda`,
and `nifi` once it exists) is **conditional**: it runs only when a trigger carries pipelines
(`hasPipelines == true`) and is resolved lazily per step. Therefore:

- A deployment **without** the pipeline adapter still boots, and pipeline-free sagas complete normally.
- A saga that *does* carry pipelines but finds no pipeline handler **fails gracefully** — the step
  raises a saga failure routed through the normal compensation/failure path, not an opaque crash.

This keeps the engine runnable during the RedPanda → NiFi migration, while the pipeline adapter may
be temporarily absent. Both the BPMN and coded variants share this behavior (enforced by the
equivalence tests).

The **geoserver** adapter is conditional in the same way: its steps run only when a trigger carries a
`POSTGIS` data sink (`hasGeoSink`), so it is **not** in `REQUIRED_HANDLERS` and a deployment without
it still boots. Its compensation (`DELETE_WORKSPACE`) is idempotent and 404-tolerant, so the
reverse-compensation chain stays safe whether or not the GeoServer branch actually ran.

## Architecture

The module is organized into three top-level packages:

- `common` — engine bootstrap, infrastructure factories, saga handler registry, JavaDelegate base classes, and the Kafka trigger/result bridge.
- `bpmn` — deployment of the XML-based BPMN process definitions from `src/main/resources/processes/`.
- `coded` — deployment of the programmatically built equivalents (`BpmnModel`-builder).

Both `bpmn` and `coded` produce equivalent process definitions and share the delegates in `common`. An equivalence test enforces this.

### Key Design Decisions

- **No Spring Boot required** — Flowable runs standalone with programmatic `ProcessEngineConfiguration`
- **Handlers reused** — Existing `SagaCommandHandler` implementations (FROST, APISIX, pipeline) called via JavaDelegate wrappers; only FROST and APISIX are required at startup (see *Adapter handlers: required vs optional*)
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
