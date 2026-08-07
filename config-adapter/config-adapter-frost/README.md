# FROST Config Adapter

Translates Civitas configuration CloudEvents into OGC SensorThings API calls against a
[FROST-Server](https://github.com/FraunhoferIOSB/FROST-Server) instance, and provides the saga command
handler that provisions one FROST project per DataSet. Entity metadata is in scope. Observation ingest
belongs to the pipeline adapter; request authorization belongs to the gateway.

## Operations

`CREATE`, `UPDATE` and `DELETE` are supported for every SensorThings entity set defined by OGC 18-088
— Things, Locations, HistoricalLocations, Sensors, ObservedProperties, Datastreams, Observations,
FeaturesOfInterest — plus the FROST `Projects` extension.

The event's `targetResource` is a SensorThings resource path. Entity-set segments are matched
case-insensitively in singular or plural form, so `Things(1)`, `Things/1` and `things/1` are
equivalent. A nested path addresses a parent scope, which is how the FROST Projects extension is used:

| `targetResource` | Operation | Request |
|---|---|---|
| `Things` | CREATE | `POST /Things` |
| `Things(1)` | UPDATE | `PATCH /Things(1)` |
| `Things(1)` | DELETE | `DELETE /Things(1)` |
| `Projects/42/Things` | CREATE | `POST /Projects(42)/Things` |
| `Things/1/Locations` | CREATE | `POST /Things(1)/Locations` |

Rejected: a path naming no known entity set (`INVALID_RESOURCE_TYPE`), and an `UPDATE` or `DELETE`
without a trailing entity id (`INVALID_PAYLOAD`).

`frost.topics` selects from `de.civitascore.data.{thing,location,sensor,observedproperty,datastream,project}.{created,updated,deleted}`.
Every configured topic MUST be one the framework knows; an unrecognised value fails startup.

The entity body is `payload.config.value` and becomes the request entity: a typed `FrostConfigValue` is
serialised through its API mapping, any other value is forwarded unchanged. A result event goes to the
`resultTopic` named in the request metadata, with `source` = `de.civitascore.config-adapter.frost` and
the `status`, `operation`, `targetResource`, `resourceId`, `correlationId` and `originalMessageId`
fields.

## Behaviour

- **Authentication is decided once.** The strategy is selected during initialization and reused for
  every request. At least one of `frost.basic.auth.username` or `frost.api.key` MUST be set, or
  initialization fails. When both are set, Basic Auth is used and a warning is logged.
- **Resource ids come from the response.** A `CREATE` answered with HTTP 201 takes the entity id from
  the `Location` header. `UPDATE` and `DELETE` report the id from the request path.
- **`UPDATE` is a partial update.** It is sent as `PATCH`, so unspecified fields keep their values.
- **Idempotency.** These outcomes are successes, not errors: HTTP 409 on `CREATE` (the entity already
  exists); HTTP 500 with a `Failed to store data.` body on `CREATE`, which is how FROST-Server cores
  before 2.7.0 answer a unique-constraint violation where cores from 2.7.0 onward answer 409 — both mean
  the entity exists; and HTTP 404 on `DELETE` (the entity is already gone).
- **Failure classification.** Connection failures and other 5xx responses are retryable and are retried
  with exponential backoff. Every other 4xx response is fatal and routes the event to the dead-letter
  queue.

## Saga participation

`FrostSagaHandler` is invoked in-process by the Flowable orchestrator; saga steps do not travel over
Kafka. It handles project-level operations only:

| Operation | Request | Compensation |
|---|---|---|
| `CREATE_PROJECT` | Name lookup, then `POST /Projects` if absent | `DELETE_PROJECT` |
| `UPDATE_PROJECT` | Reads the current name and description, then `PATCH /Projects({projectId})`, or creates the project when the DataSet has none | `RESTORE_PROJECT` |
| `DELETE_PROJECT` | Deletes the project's Things, then `DELETE /Projects({projectId})` | — |
| `RESTORE_PROJECT` | `PATCH /Projects({projectId})` with the captured previous state, or the delete when the update provisioned the project | — |

- **The project name is unique per DataSet:** `"{datasetName} ({datasetId})"`. The portal permits
  duplicate display names while FROST enforces project-name uniqueness, so the id is part of the name
  and a name match only ever resolves to that DataSet's own project.
- **A project exists only for a DataSet with a FROST data sink.** The orchestrator gates `CREATE_PROJECT`
  and `UPDATE_PROJECT` on it and leaves `DELETE_PROJECT` ungated — see
  [../config-adapter-flowable/README.md](../config-adapter-flowable/README.md).
- **`CREATE_PROJECT` finds or creates.** An existing project under the derived name is reused, keeping
  its Things, Datastreams and Observations intact. A 409 or a duplicate-signalling 500 from the POST
  re-runs the lookup as a race guard.
- **`UPDATE_PROJECT` provisions what it cannot patch.** A DataSet released without a FROST sink has no
  project, so a later update creates one rather than failing, and its compensation deletes it rather than
  restoring a state never captured.
- **A reused project is never deleted by compensation.** `CREATE_PROJECT` records whether it created the
  project, so compensating a reused project is a no-op and a later step's failure cannot destroy the data
  a re-release is meant to reuse.
- **Projects are always private.** Create, update and restore all force the project non-public. Open
  data access is an authorization decision made per request at the gateway, never granted through FROST
  project visibility.
- **`DELETE_PROJECT` is idempotent.** An absent `projectId`, and an absent project (HTTP 404), are both the
  goal state and succeed forward and compensating. Deleting a Thing cascades to its Datastreams and
  Observations, while deleting a project cascades to nothing — which is why the Things are removed first.
- **Results carry the public service root.** `baseUrl` derives from `frost.public.url`, so consumers
  receive a reachable URL even when the adapter itself reaches FROST over an internal address.

## Configuration

Keys carry the `frost.` prefix. The values that ship, env-var names, secret handling and container
configuration live in [../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Role |
|---|---|
| `frost.topics` | Required for config events; without it the adapter subscribes to nothing |
| `frost.url` | Base URL at which the adapter reaches FROST; a trailing slash is stripped |
| `frost.public.url` | Externally visible base URL reported in saga results; read by the saga handler only |
| `frost.basic.auth.username` / `.password` | Basic Auth credentials |
| `frost.api.key` | API key, as an alternative to Basic Auth |
| `frost.api.key.header` | Header carrying the API key |

One of `frost.basic.auth.username` or `frost.api.key` MUST be set; the Basic Auth user takes precedence.

## Error codes

| Code | Name | Meaning | Retryable |
|---|---|---|---|
| 1001 | `INVALID_PAYLOAD` | `UPDATE` or `DELETE` without an entity id | no |
| 1005 | `INVALID_RESOURCE_TYPE` | `targetResource` names no known entity set | no |
| 2002 | `SERVICE_UNAVAILABLE` | FROST answered 5xx | yes |
| 2003 | `NETWORK_ERROR` | The request did not reach FROST | yes |
| 3201 | `FROST_ENTITY_ERROR` | FROST answered 4xx, or the request failed unexpectedly | no |

## Testing

```bash
mvn test -pl config-adapter-frost      # unit tests, no Docker
mvn verify -pl config-adapter-frost    # adds integration tests, requires Docker
```

Integration tests run against FROST-Server and PostGIS containers started by Testcontainers. They need
`--add-opens java.base/java.net=ALL-UNNAMED`, which the module's failsafe configuration supplies.
