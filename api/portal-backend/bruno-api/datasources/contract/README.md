# Data source connector contract tests

HTTP characterization tests that pin the connector contract of the
`POST/GET/PUT/PATCH/DELETE /datasources` endpoints and of
`POST /datasources/{id}/release`.

## The connector shape

A request and a response carry the connector as one member, `connector`.
It holds `connectorType` (`MQTT` or `SQL`) and the fields of that type:

```json
"connector": { "connectorType": "SQL", "driver": "postgres", "dsn": "postgres://db:5432/x", "table": "t", "columns": ["id"], "where": null, "user": "u", "password": "secret" }
"connector": { "connectorType": "MQTT", "urls": ["tcp://broker:1883"], "topics": ["a/#"], "qos": 1, "protocol_version": "3", "connect_timeout": "5s", "keepalive": "30s", "tls": { "enabled": false }, "user": null, "password": null }
```

- **Response:** `connector` is null when the Data source has no connector
  type. Otherwise it has every field of the type, null when unset. A
  stored password reads `"********"`, no password reads `null`. MQTT
  `protocol_version` reads `"3"` when unset, `tls` reads
  `{ "enabled": false }` when unset.
- **POST/PUT:** `connector` is the full connector. A PUT without
  `connector` removes it.
- **PATCH:** `connector` is a JSON merge patch (RFC 7386) of the stored
  connector. An omitted key keeps its value; `null` or a blank string
  clears it. A `connectorType` different from the stored one replaces the
  connector. `"connector": null` removes it; an omitted `connector` keeps
  it.
- **Password placeholder:** `"password": "********"` in a write keeps the
  stored password. With no stored password, none is stored.

## Errors

| Case | Status / `type` | Body |
|---|---|---|
| Unknown key, also nested | 400 `MALFORMED_REQUEST` | `detail`: `Unknown key: connector.<path>` |
| Unknown `connectorType` | 400 `MALFORMED_REQUEST` | `detail`: `Unknown connectorType: <value>` |
| `connectorType` missing | 400 `MALFORMED_REQUEST` | `detail`: `Missing connector.connectorType` |
| Wrong value type, invalid URL or DSN | 400 `MALFORMED_REQUEST` | `detail`: `Invalid value: connector.<path>` |
| Value out of range on POST/PUT | 400 `VALIDATION_FAILED` | `fieldErrors[].field`: `connector.<key>` |
| Release with missing required fields | 400 `VALIDATION_FAILED` | `fieldErrors[].field`: `connector.<key>` |
| Release without a stored connector | 400 `INVALID_INPUT` | `detail`: `Configuration is required for releasing` |

A `detail` never echoes a value, because a value can carry a credential.

## How the tests are organised

Each logical test is a chain of `.bru` files that share state through a
collection-scoped variable. Files are named
`<connector>-<scenario>-<step>-<action>.bru` so Bruno's alphabetical run
order keeps the steps together.

A typical chain looks like:

1. `*-1-create.bru` — POST a data source, capture `id` via `bru.setVar(...)`.
2. `*-2-*.bru` — GET/PUT/PATCH using the captured id.
3. `*-3-read.bru` — re-read and assert the persisted shape.
4. `*-N-cleanup.bru` — DELETE the resource so parallel runs and reruns
   don't leave orphans.

A request that the server rejects creates nothing, so the single-request
error tests (`*-validation-*`, `*-unknown-*-rejected`) have no cleanup
step.

Each test is self-contained — it doesn't depend on resources created by
the `00-setup/` folder. Most scenarios pass `dataStructureVersionId: null`,
which the contract accepts and which keeps them independent of any shared
setup state. Release-lifecycle chains (`*-release-rejects-empty-*`) create
and link their own DSV within the chain.

## Deferred validation

`*-empty-config-accepted-*.bru` pins that a DRAFT Data source accepts a
connector with only `connectorType`: the required fields are checked at
release, not on create or update. `*-release-rejects-empty-configuration-*.bru`
pins the release half. If either half changes, revisit both together.
Range checks such as MQTT `qos` run on every write
(`mqtt-validation-qos-out-of-range.bru`).

## Scenarios

| Chain | Pins |
|---|---|
| `sql-create-and-read-*`, `mqtt-create-and-read-*` | Response shape, defaults, masked password |
| `sql-update-masked-secret-*` | PUT with `"********"` keeps the stored password |
| `sql-masked-password-without-stored-secret-*` | PUT with `"********"` and no stored password stores none |
| `sql-patch-partial-*`, `mqtt-patch-partial-*` | PATCH without `connector` keeps it |
| `sql-patch-clears-field-*` | PATCH with `null` or `""` clears a key, omitted keys stay |
| `mqtt-patch-changes-connector-type-*` | PATCH with another `connectorType` replaces the connector |
| `sql-put-changes-connector-type-*` | PUT with another `connectorType` on DRAFT replaces the connector |
| `mqtt-update-arrays-*` | PUT replaces array fields |
| `sql-delete-and-404-*`, `mqtt-delete-and-404-*` | DELETE, then 404 |
| `sql-sslmode-roundtrip-*` | `sslmode` survives the credential stripping of the DSN |
| `sql-unknown-fields-rejected`, `mqtt-unknown-nested-key-rejected` | Unknown keys are rejected |
| `sql-validation-*`, `mqtt-validation-*` | Error responses for an unreadable or invalid connector |
