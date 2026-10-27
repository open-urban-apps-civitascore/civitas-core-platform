# Data source connector contract tests

HTTP characterization tests that pin the connector contract of the
`POST/GET/PUT/PATCH/DELETE /datasources` endpoints, of
`POST /datasources/{id}/release` and of the connection test,
`POST /datasources/connection-test` and `POST /datasources/{id}/connection-test`.

## The connector shape

A request and a response carry the connector as one member, `connector`.
It holds `connectorType` (`MQTT`, `SQL` or `HTTP`) and the fields of that type:

```json
"connector": { "connectorType": "SQL", "driver": "postgres", "dsn": "postgres://db:5432/x", "table": "t", "columns": ["id"], "where": null, "user": "u", "password": "secret" }
"connector": { "connectorType": "MQTT", "urls": ["tcp://broker:1883"], "topics": ["a/#"], "qos": 1, "protocol_version": "3", "connect_timeout": "5s", "keepalive": "30s", "tls": { "enabled": false }, "user": null, "password": null }
"connector": { "connectorType": "HTTP", "baseUrl": "https://api.example.org/v1", "authentication": { "type": "BASIC", "username": "reader", "password": "secret" }, "apiCalls": [{ "name": "stations", "method": "GET", "path": "/stations", "queryParameters": [{ "name": "status", "value": "active" }], "headers": null, "pagination": { "type": "PAGE_NUMBER", "pageParameter": "page", "pageSizeParameter": "size", "pageSize": 100, "startPage": 0, "endPage": null } }] }
```

- **Response:** `connector` is null when the Data source has no connector
  type. Otherwise it has every field of the type, null when unset. A
  stored secret reads `"********"`, no secret reads `null`. The secrets
  are each `password`, and the HTTP `authentication.token`, `.value`,
  `.clientSecret`, `.certificate` and `.privateKey`. MQTT
  `protocol_version` reads `"3"` when unset, `tls` reads
  `{ "enabled": false }` when unset. HTTP `authentication` reads
  `{ "type": "NONE" }` when unset, `startPage` reads `0` when unset, and
  `method` always reads `"GET"`; a `method` in a write is ignored.
- **POST/PUT:** `connector` is the full connector. A PUT without
  `connector` removes it.
- **PATCH:** `connector` is a JSON merge patch (RFC 7396) of the stored
  connector. An omitted key keeps its value; `null` or a blank string
  clears it. A `connectorType` different from the stored one replaces the
  connector. The same holds at every depth: a nested object whose `type`
  differs from the stored one is replaced, not merged.
  `"connector": null` removes it; an omitted `connector` keeps it.
- **Secret placeholder:** `"********"` for a secret in a write keeps the
  stored secret at the same place, and so does a PATCH that omits it.
  This holds only while every destination stays: scheme, host and port of
  each address, the OAuth2 token URL included, and `sslmode`. With no
  stored secret there, for example after a change of `authentication.type`,
  none is stored.

## Errors

| Case | Status / `type` | Body |
|---|---|---|
| Unknown key, at the top level or in the connector | 400 `MALFORMED_REQUEST` | `detail`: `Unknown key: <key>` or `Unknown key: connector.<path>` |
| Unknown `connectorType` | 400 `MALFORMED_REQUEST` | `detail`: `Unknown connectorType: <value>` |
| `connectorType` missing | 400 `MALFORMED_REQUEST` | `detail`: `Missing connector.connectorType` |
| Unknown nested `type` | 400 `MALFORMED_REQUEST` | `detail`: `Unknown connector.<path>.type: <value>` |
| Nested `type` missing | 400 `MALFORMED_REQUEST` | `detail`: `Missing connector.<path>.type` |
| Wrong value type, number with a fraction for an integer, invalid URL or DSN | 400 `MALFORMED_REQUEST` | `detail`: `Invalid value: connector.<path>` |
| Value out of range, invalid pattern or duplicate name on POST/PUT/PATCH | 400 `VALIDATION_FAILED` | `fieldErrors[].field`: `connector.<path>` |
| Release with missing required fields | 400 `VALIDATION_FAILED` | `fieldErrors[].field`: `connector.<path>` |
| Release without a stored connector | 400 `INVALID_INPUT` | `detail`: `Configuration is required for releasing` |
| Connection test with missing required fields | 400 `VALIDATION_FAILED` | `fieldErrors[].field`: `connector.<path>` |
| Connection test whose `apiCall` is missing for HTTP, names no API call of the connector, or is set for SQL or MQTT | 400 `VALIDATION_FAILED` | `fieldErrors[].field`: `apiCall` |
| Connection test with `"********"` and no stored secret at its place | 400 `MALFORMED_REQUEST` | `detail`: `No stored secret for ******** at connector.<path>` |
| Write or connection test of a saved data source that keeps the stored password for another destination (scheme, host, port or `sslmode`) | 400 `MALFORMED_REQUEST` | `detail`: `The stored secret is kept only for the stored destination; …` |
| Connection test of a released data source with `"********"` and changed values | 400 `MALFORMED_REQUEST` | `detail`: `The stored secret of a released data source is kept only to test its connector as released; …` |

A `detail` echoes no value other than a `connectorType` or a nested `type`, because a value
can carry a credential.

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
setup state. Release-lifecycle chains (`*-release-rejects-*`) create
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
| `sql-create-and-read-*`, `mqtt-create-and-read-*`, `http-create-and-read-*` | Response shape, defaults, masked password |
| `http-bearer-token-*`, `http-api-token-*`, `http-oauth2-*`, `http-client-certificate-*` | Each HTTP authentication reads back with its secret masked |
| `http-pagination-create-and-read-*` | Limit/offset and cursor pagination read back as sent |
| `sql-update-masked-secret-*`, `http-update-masked-secret-*` | PUT with `"********"` keeps the stored password |
| `sql-masked-password-without-stored-secret-*` | PUT with `"********"` and no stored password stores none |
| `sql-patch-other-host-*` | PATCH of another host keeps no stored password and is refused |
| `sql-patch-partial-*`, `mqtt-patch-partial-*` | PATCH without `connector` keeps it |
| `sql-patch-clears-field-*` | PATCH with `null` or `""` clears a key, omitted keys stay |
| `mqtt-patch-changes-connector-type-*` | PATCH with another `connectorType` replaces the connector |
| `http-patch-changes-auth-type-*` | PATCH with another `authentication.type` replaces the authentication and drops its password |
| `sql-put-changes-connector-type-*` | PUT with another `connectorType` on DRAFT replaces the connector |
| `mqtt-update-arrays-*` | PUT replaces array fields |
| `sql-delete-and-404-*`, `mqtt-delete-and-404-*`, `http-delete-and-404-*` | DELETE, then 404 |
| `sql-sslmode-roundtrip-*` | `sslmode` survives the credential stripping of the DSN |
| `sql-unknown-fields-rejected`, `mqtt-unknown-nested-key-rejected`, `http-unknown-nested-key-rejected` | Unknown keys are rejected |
| `sql-validation-*`, `mqtt-validation-*`, `http-validation-*` | Error responses for an unreadable or invalid connector |
| `http-release-rejects-invalid-configuration-*` | Release names each missing field of the authentication and of an API call by its path, and a limit without its parameter |
| `sql-connection-test-answers`, `mqtt-connection-test-answers`, `http-connection-test-answers` | A connection test of unsaved values with a `.invalid` host answers `SOURCE_ERROR` |
| `sql-connection-test-rejects-*`, `http-connection-test-rejects-*` | A connection test rejects incomplete values, a placeholder of unsaved values and a missing `apiCall` before it runs |
| `sql-connection-test-saved-masked-password-*` | A connection test of a saved data source keeps the stored password for `"********"` |
