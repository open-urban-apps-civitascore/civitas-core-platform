# Flowable Saga Orchestrator

Orchestrates the dataset lifecycle sagas across the FROST, APISIX, PostGIS, GeoServer and NiFi pipeline adapters. The
Flowable engine runs embedded in the config-adapter JVM — no separate server — with the sagas defined as BPMN 2.0
processes under `src/main/resources/processes/` and state persisted in a PostgreSQL database of its own. Sequencing,
branching and compensation are in scope; the work of a step belongs to that adapter's saga command handler.

## Saga processes

One BPMN process per saga type; the trigger's `sagaType` selects it.

| `sagaType` | Process | Step order | On step failure |
|---|---|---|---|
| `DATASET_CREATE` | `dataset-create` | FROST `CREATE_PROJECT` → APISIX `CREATE_ROUTE` → geo branch → pipeline branch | reverse-order compensation |
| `DATASET_UPDATE` | `dataset-update` | FROST `UPDATE_PROJECT` → APISIX `UPDATE_ROUTE` → geo branch → pipeline branch → GeoServer `PRUNE_FEATURE_TYPES` when `hasGeoSink` | reverse-order compensation; a failing prune is absorbed and the saga still succeeds |
| `DATASET_DELETE` | `dataset-delete` | pipeline branch → APISIX `DELETE_ROUTE` → geo teardown → FROST `DELETE_PROJECT` | best-effort: the chain continues, no compensation |
| `DATASET_UNRELEASE` | `dataset-unrelease` | pipeline branch → APISIX `DELETE_ROUTE` | best-effort: the chain continues, no compensation |

| Saga | Geo branch (`hasGeoSink`) | Pipeline branch (`hasPipelines`) |
|---|---|---|
| Create | PostGIS `PROVISION_SINK` → GeoServer `CREATE_WORKSPACE` → `CREATE_DATASTORE` → `PROVISION_LAYERS` when `hasLayers` | `DEPLOY_PIPELINES` |
| Update | GeoServer `UPDATE_WORKSPACE` | `UPDATE_PIPELINES` |
| Delete | GeoServer `DELETE_WORKSPACE` → PostGIS `DEPROVISION_SINK` | `DELETE_PIPELINES` |
| Unrelease | — | `DELETE_PIPELINES` |

The update saga's prune sits behind its own `hasGeoSink` gateway after the pipeline branch: a pruned feature type
cannot be restored, so it MUST follow the last failable step — see
[../config-adapter-geoserver/README.md](../config-adapter-geoserver/README.md). `DATASET_UNRELEASE` tears down ingest
and consumer access only; the data-holding sink — the PostGIS table and the FROST project — is kept so a later
re-release reuses it.

Compensation chains run in full reverse order:

| Saga | Chain |
|---|---|
| Create | `DELETE_PIPELINES` → `DELETE_WORKSPACE` → `DEPROVISION_SINK` → `DELETE_ROUTE` → `DELETE_PROJECT` |
| Update | `DELETE_PIPELINES` → `RESTORE_WORKSPACE` → `RESTORE_ROUTE` → `RESTORE_PROJECT` |

The GeoServer link of each chain is gated on the same derived flag as the forward branch, so a saga that never touched
GeoServer attempts no teardown.

## Branch flags

Derived from the trigger payload by the consumer and never read from it. Gating the geo branch keeps a deployment
without a GeoServer adapter from failing every delete, and avoids a recursive workspace delete on a dataset that has
no geo data.

| Flag | True when |
|---|---|
| `hasGeoSink` | `datasinks` is a list holding an entry whose `type` is `POSTGIS` |
| `hasLayers` | `layers` is a non-empty list |
| `hasPipelines` | `dataPipelines` or `pipelineIds` is a non-empty list |

## Behaviour

- **Steps execute in-process.** A step resolves the adapter's `SagaCommandHandler` from the registry by name and calls
  it directly, so no Kafka round trip is paid per step and no step-level topics exist.
- **`frost` and `apisix` MUST be registered** — the startup check demands both regardless of which sagas the
  deployment runs, and their absence fails startup. `nifi`, `geoserver` and `postgis` resolve lazily per step, so a
  deployment without them boots; a forward step reaching an absent handler raises a saga failure routed through the
  normal path rather than crashing.
- **Compensation is best-effort.** A teardown that fails or finds no handler is recorded in `compensationErrors` and
  the chain continues; the result then reports `FAILED` rather than `COMPENSATED`. Each step records what it created
  under its own step id and its teardown acts on that record, so a teardown is never gated on configuration.
- **Triggers are idempotent.** The Kafka record coordinate — topic, partition and offset — is the process business
  key, checked against both running and finished instances, so a redelivered record starts no second saga.
- **A malformed trigger is dropped, not retried.** A missing or non-string `sagaType`/`datasetId`, or an unknown saga
  type, is logged and its offset committed. A transient failure instead seeks every partition of the batch back to
  its last safe offset, so nothing is lost.
- **Process deployment is idempotent.** Duplicate filtering means a restart adds a process version only when a BPMN
  file's content changed.
- **Schema creation and crash recovery are automatic.** Flowable creates and updates its own `ACT_`-prefixed tables
  against the configured database and runs its async executor, which is what lets a saga resume after a crash.
- **A success result carries only** `sagaId`, `datasetId` and the keys the step handlers produced, never the original
  trigger payload fields.
- **`DEPROVISION_SINK` drops the per-dataset schema.** It drops the read role, then the sink table, then the dataset's
  own schema with `RESTRICT`, skipping `public`. A schema left non-empty fails the step and nothing is force-dropped;
  in the best-effort delete saga the chain continues past that failure.

The module defines no adapter error codes: a failing step raises a BPMN error routed to the compensation or
best-effort path, and the codes a step produced belong to the adapter that ran it.

## Kafka contract

Only the saga boundary crosses Kafka. `sagaType` is a payload field, not a topic: one trigger topic carries
every saga type, and steps are dispatched in-process from there.

| Topic | Direction | Key | Payload |
|---|---|---|---|
| `de.civitascore.dataset.saga.trigger` | consumed | — | `sagaType`, `datasetId`, and the fields the steps consume: `datasinks`, `layers`, `styles`, pipelines |
| `de.civitascore.saga.result` | published | `sagaId` | `SAGA_COMPLETED`, or `SAGA_FAILED` with `status` `FAILED` or `COMPENSATED` |
| named by `pipeline.status-topic` | published | `datasetId/pipelineId` | pipeline runtime status |

The result producer runs with `acks=all` and idempotence enabled.

## Configuration

Env vars, production values and database provisioning live in [../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Coded default |
|---|---|
| `flowable.jdbc.url` | — |
| `flowable.jdbc.username` | — |
| `flowable.jdbc.password` | — **required** |
| `flowable.kafka.group.id` | `config-adapter-flowable-group` |
| `kafka.bootstrap.servers` | `localhost:9092` |
| `pipeline.status-topic` | `de.civitascore.pipeline.status` |

The three JDBC properties have no coded default, and all three MUST resolve or startup fails with an
incomplete-configuration error. The packaged `application.properties` supplies a url and a username, so only an
unresolved password fails startup; an unset `FLOWABLE_JDBC_URL` connects to the packaged database instead. An empty
environment variable resolves as unset, so the properties file value applies. Flowable requires a database of its
own, separate from any other component's.

## Testing

```bash
mvn test -pl config-adapter-flowable          # unit and process tests on in-memory H2, no Docker
mvn -pl config-adapter-flowable -am verify    # adds integration tests, requires Docker
```

Test engines run synchronously so process assertions are deterministic; deployed engines run the async
executor. Container image tags live in `TestContainerImages`. The process files carry BPMN DI layout, which a
BPMN 2.0 modeler generates and bpmn.io needs to render them.
