# NiFi Pipeline Config Adapter

Compiles the engine-neutral **pipeline graph** authored in the portal's pipeline editor into a curated Apache NiFi flow and deploys, redeploys and tears it down over NiFi's REST API — one process group per pipeline. Its entry point is a saga step handler discovered through the `ServiceLoader`: it consumes no CloudEvents, subscribes to no Kafka topics, and MUST NOT appear in `adapters`. Out of scope are the sink's schema and table DDL (the PostGIS adapter owns those), SensorThings Datastream provisioning for passthrough flows, and any secret inside the uploaded flow definition.

## Operations

| Operation | Effect | Result data |
|---|---|---|
| `DEPLOY_PIPELINES` | Deploys every pipeline of the dataset | `pipelineIds`, `nifiProcessGroupIds` |
| `UPDATE_PIPELINES` | Per pipeline by its `action`: `ADD`/`UPDATE` deploy, `DELETE` removes | `pipelineIds` |
| `DELETE_PIPELINES` | Tears down the listed pipelines | — |
| `RESTORE_PIPELINES` | Re-deploys the listed pipelines as the compensation of an update | — |

Any other operation, and any unknown per-pipeline `action`, fails naming the offending value. The command payload carries the dataset's pipelines (id, pipeline graph, associated datasource and datasink ids), the dataset-wide catalogs those ids resolve against, and two saga-wide variables: the FROST project id, and the dataset's technical id from which the per-DataSet PostGIS schema is derived.

### Rejected — every rejection below is fatal and happens before the first REST call

- **Graph** — a missing or duplicate node id, an edge to an undeclared node, a `nodes`/`edges` member that is not a list of objects.
- **Topology** — not exactly one source and one sink; an unknown node kind wired into the flow; a branch; a cycle; a source with an incoming or a sink with an outgoing data edge; no path from source to sink; a transform or cron node wired off the derived flow path; more than one trigger binding; a cron node without an expression.
- **Payload** — a pipeline whose graph member is present but not an object, a blank pipeline id or action, a catalog member of the wrong element type.
- **Association** — a source or sink node with no configured entity id, an entity id absent from the trigger's catalog, or a pipeline carrying no association ids at all (reported as its own condition).
- **Combination** — an unsupported source or sink type; a source payload form the sink can neither consume nor have converted; a cron on a push-based source; a cron expression that is not exactly six whitespace-separated fields; a SQL source writing to PostGIS with no resolvable primary key.
- **Source configuration** — a plaintext password (secrets MUST arrive encrypted); an MQTT broker scheme that is unsupported or disagrees with the datasource's TLS switch; a path on a `tcp`/`ssl` broker URL; brokers spread over more than one transport; more than one MQTT topic filter; a timeout that is not a plain seconds duration; a SQL source the pre-deploy JDBC probe cannot reach with the resolved credentials.
- **SQL hardening** — a `dsn` query parameter outside the permitted allowlist; a bind placeholder in `where`; query-shaping fields with no NiFi equivalent; a driver other than PostgreSQL; a NiFi Expression Language reference in `table`, `columns` or `where`.
- **Sink prerequisites** — a FROST sink without the saga's numeric project id or without `nifi.frost.url`; a mapped FROST sink whose datasink carries no target data structure; a PostGIS sink without a table name or without `nifi.postgis.url`.
- **Mapping** — any grammar violation; a mapping node without a configuration; a chain whose neighbours disagree on the structure handed between them; an unresolvable fan-out; a violated FROST target rule.

### Sources, transforms and sinks

**Payload forms** on an edge are `RAW_JSON`, `STA_ENVELOPE` and `RECORDS`. The first two are convertible to `RECORDS` through a convert step the builder inserts structurally, never user-modelled. The editor mirrors this vocabulary, so both layers reach the same verdict on the same graph.

| Source | Datasource types | Emits | Trigger binding | Required fields |
|---|---|---|---|---|
| MQTT (push) | `mqtt` | `STA_ENVELOPE` | Rejected — the source self-triggers on broker messages | Broker URLs, exactly one topic filter |
| SQL (pull) | `sql`, `postgresql`, `postgres`, `jdbc` | `RECORDS` | Accepted | Table, DSN |

Every MQTT flow connects with its own client id, so flows that share a datasource do not evict each other's broker session. The id is `civitascore` plus a base36 hash of pipeline id and source node id: alphanumeric and within the 23 characters every MQTT broker must accept, and the same on every deploy of that node.

A TLS MQTT broker is supported: the flow mints an SSL context service over the truststore that `nifi.mqtt.truststore.*` names — the JVM's own trust store by default, which carries the public root CAs, so a publicly trusted broker certificate needs no configuration. A store with a real password is opened through a deployment-owned Parameter Context the flow declares but never carries a value for; a well-known one (the JDK's `changeit`) through a literal pushed onto the controller service after upload. A SQL source re-reads the whole table on every run and tracks no high-water column, on an explicit cron or the source fragment's built-in schedule.

| Sink | Datasink types | Accepts | Mapping | Write behaviour |
|---|---|---|---|---|
| PostGIS | `postgis`, `postgresql`, `postgres` | `RECORDS` | Compiled RecordPath writes the sink's record shape directly | One terminal record-writing processor over the platform-managed pool, into the per-DataSet schema |
| FROST | `frost`, `sta` | `STA_ENVELOPE` without a mapping, `RECORDS` with one | Compiled to flat fields handed to the sink's own request chain | Passthrough: two independent upsert legs over the source's own SensorThings envelope. Mapped: one linear per-record find-or-create chain |

The single on-path transform kind is the record mapping; instances are unbounded and chainable. Its grammar is closed: a field value is a source path, or one of copy, constant, concatenation, geographic point and the conversions `toString`, `toInt`, `toFloat`, `toDate` and `format`. Numeric conversions are transparent — coercion happens at the sink against the real column types, so the compilation needs no schema knowledge. Geometry is the one axis on which compilation depends on the sink: WKT for PostGIS, a GeoJSON object for FROST.

## Behaviour

- **Three phases complete before the first REST call.** Derivation parses the graph and resolves the linear path from source to sink, dispatching on node roles rather than raw type strings and failing loud on anything that would silently change that path. Planning resolves the datasource and a typed sink specification from the catalogs, has each transform kind compile its own nodes, and decrypts credentials into a separate sensitive map. Building composes source → convert → transform… → sink.
- **Only whitelisted components can be minted.** Every processor and controller service comes from a curated set of flow fragments, so no tenant-derived value reaches the component loader and neither scripting nor Jolt is reachable from this module. The table binding each source type, sink type and transform kind to its implementation is hand-wired at handler initialization rather than classpath-discovered, and its construction fails when a declared kind has no implementation — vocabulary drift breaks adapter startup instead of a deployment.
- **Redeploys are idempotent.** The process group is resolved by a stable name derived from the pipeline id, stopped and deleted, then uploaded afresh. An unchanged pipeline graph yields a byte-identical flow definition whose components keep the identity they had in the previous deployment. Property and array insertion order, fragment resource names and controller-service friendly names all take part in that identity: changing any of them replaces every component in a live NiFi even though the graph did not change.
- **A mapping fans out on arrays.** A mapping reading source paths below an array becomes one record per element ahead of its own processors, forking on the innermost shared array and derived from the source side only. Shapes that would fan out into nothing, into ambiguity, or into silently duplicated rows are rejected at compile time; a fan-out yielding zero records at runtime routes to the error sink instead of travelling on as an empty success. A multi-node mapping chain is only shallowly supported: each node compiles against the paths it was authored with, so neighbours agree on a declared structure rather than on the shape actually emitted.
- **PostGIS writes are keyed.** With resolved primary-key columns the write is an upsert on them, so a re-reading scheduled source updates rows instead of duplicating them; without them it is a plain insert. The keys come from the shared explicit-wins-else-marker rule, so they are identical to the table's primary key. A fan-out whose every key column is mapped from outside the array is rejected — all rows would share one key.
- **A mapped FROST flow is validated against its target structure.** Only the curated catalog paths and the structure's `properties`-bag attributes may be mapped. The Thing's match key MUST be mapped, and the Datastream's once any Datastream or Observation path is touched. Each entity's create set is all-or-nothing: fully mapped makes the entity creatable, none mapped leaves it lookup-only, a partial set is rejected. A mapped Observation MUST map its result, and a FeatureOfInterest MUST accompany a mapped Observation.
- **No deployed flow drops data silently.** Every failure relationship routes to a shared log-based error sink, and auto-termination is removed wherever a relationship becomes connected. The graph shape is stable so the error sink can be replaced by a durable dead-letter target without rewiring around it.
- **The uploaded flow carries no secrets.** Sensitive properties are pushed after upload and matched on a component's friendly name, never on its type, so a second component of the same type cannot receive the wrong secret. A secret matching no component fails the deployment rather than being dropped — which is also why renaming a component breaks the push.
- **The handler is optional at boot.** A failed initialization drops it and the application boots without it, naming the dropped handler in the startup log; datasets needing a pipeline then fail at their deployment step. Most misconfiguration — an absent FROST or PostGIS URL, an absent OIDC client secret or master key — does not fail initialization at all, only the first affected deployment. It is not gated by the `adapters` list, which governs Kafka-consuming adapters only.
- **A daemon thread reports runtime state.** It polls NiFi's bulletin board and per-group processor states, discovers managed process groups it did not deploy itself, and publishes state transitions as pipeline status events. An error is published once when a pipeline leaves the healthy state; a return to health only after several consecutive healthy polls. Every message and stack trace leaving the adapter has URLs and credential-shaped values redacted, and a published failure carries the error code's safe external message.

Deploy and redeploy touch these resources in order.

1. The OpenID Connect provider's token endpoint. The adapter sends the client-credentials access token as a bearer token, caches it until shortly before expiry, and on a 401 refreshes once and replays. This endpoint has its own certificate-validating client, so relaxing TLS verification for NiFi never relaxes it here.
2. The root process group. The 403 an OIDC-secured NiFi returns before the service account holds canvas rights self-heals: the missing root read and write policies are provisioned from the global rights the account already holds, then the step continues. It is a no-op once the policies exist.
3. The root's children, to resolve the process group by name. An existing group is stopped, waited out, its queues emptied, its controller services disabled and waited out, then deleted. Emptying the queues is what makes the delete possible at all: NiFi refuses to delete a group whose connections still hold FlowFiles, and stopping the group does not discard them. The drop runs after the group has stopped, because it only discards what is queued when it is submitted — a still-scheduled source would refill the queues behind it. 
4. The process-group upload endpoint, which receives the flow definition.
5. The controller-service and processor endpoints, to patch sensitive properties.
6. The group's controller-service state endpoint, then polling until all are enabled.
7. The group's state endpoint, then polling until every processor is running.

A failure at or after step 4 deletes the half-deployed group on a best-effort basis before the original error propagates. A teardown resolves the group by name as in step 3 and runs the same stop, empty, disable and delete sequence; a group that is already absent is a no-op. Queued data is discarded without being read, so a teardown logs how many FlowFiles went.

| Outcome | Classification |
|---|---|
| 2xx | Success |
| 5xx, 408, 409, 429 | Retryable — a component that has not finished starting, or a revision moved by a concurrent edit |
| Other non-2xx | Fatal |
| Transport failure | Retryable |
| 404 on delete | Success — teardown is idempotent |
| A component reporting `INVALID` | Fatal immediately, carrying NiFi's own validation messages — a retryable classification would loop forever under redelivery |
| A bounded poll that times out | Retryable |

## Configuration

Keys are read under the `nifi.` prefix. The values that ship, environment variables and hardening live in [../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Role |
|---|---|
| `nifi.url` | Base URL of the NiFi REST API |
| `nifi.tls.insecure` | MUST be `false` outside development; logs a warning while enabled |
| `nifi.oidc.token-uri` / `.client-id` / `.client-secret` / `.scope` | Client-credentials token endpoint and client. The secret is required; NiFi authentication fails until it is set |
| `nifi.frost.url` | Required for FROST pipelines |
| `nifi.frost.basic.auth.username` / `.password` | Credentials the generated flow uses for FROST; fall back to the unprefixed `frost.basic.auth.*`. Absent credentials leave the flow's FROST calls unauthenticated; the password is required once a username is set |
| `nifi.postgis.url` / `.user` / `.password` | Required for PostGIS pipelines |
| `nifi.mqtt.truststore.path` / `.type` | Trust anchor for MQTT broker certificates, resolved inside NiFi. Required once a datasource enables TLS |
| `nifi.mqtt.truststore.password` | Literal store password, pushed after upload rather than written into the flow. For well-known passwords only; discarded when a password parameter is set |
| `nifi.mqtt.truststore.password-parameter` / `.parameter-context` | Names of the sensitive parameter and its Parameter Context; `none` as the parameter declares a truststore without a password |
| `nifi.runtime-monitor.interval-ms` | Poll interval of the runtime status thread |
| `nifi.master-key` | Falls back to `CIVITAS_MASTER_KEY`; required once any datasource carries an `ENC(...)` value |

## Error codes

| Code | Name | Meaning | Retryable |
|---|---|---|---|
| 1001 | `INVALID_PAYLOAD` | The saga command payload or a typed sink specification is malformed | no |
| 2003 | `NETWORK_ERROR` | Transport failure to NiFi or to the token endpoint | yes |
| 3601 | `NIFI_ERROR` | NiFi answered 5xx, 408, 409 or 429; a state transition timed out or was interrupted; a freshly refreshed token was rejected; the token endpoint answered 5xx, 408 or 429 | yes |
| 3602 | `NIFI_FLOW_ERROR` | NiFi answered 4xx; the flow definition could not be assembled; a fragment or controller-service reference is missing; a component reported `INVALID`; a sensitive property matched no component; credential decryption failed or the master key is absent | no |
| 3603 | `NIFI_TEMPLATE_ERROR` | A graph, topology, source, sink, trigger or compatibility rule was violated | no |
| 3604 | `NIFI_MAPPING_ERROR` | The mapping grammar or a FROST mapping rule was violated | no |
| 3605 | `NIFI_AUTH_ERROR` | The token endpoint rejected the client-credentials request or returned no token; NiFi reported no identity, or an identity with no NiFi user | no |

## Testing

```bash
mvn test -pl config-adapter-nifi          # unit tests, no Docker
mvn -pl config-adapter-nifi -am verify    # adds integration tests and static analysis, requires Docker
```

`*Test` runs under Surefire, `*IT` under Failsafe; neither needs an extra JVM argument. Integration tests run against NiFi, an OpenID Connect provider, PostGIS and a SensorThings server in containers, and skip when no Docker daemon is available. Static analysis runs in `verify`, not in `test`. An architecture suite enforces that scripting and Jolt stay unreachable, that the stage, mapping and credential layers stay transport-free, that the REST layer consumes only the finished deployment plan, and that no package cycle exists.
