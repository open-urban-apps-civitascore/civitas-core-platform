# PostGIS Config Adapter

Adapter for managing PostgreSQL/PostGIS **tables**, **schemas**, and **roles** (including schema-level grants) via DDL, driven by CloudEvents on Kafka.

## Overview

`PostgisAdapter` applies DDL changes to a target Postgres database. The payload type selects the resource family; the operation (CREATE / UPDATE / DELETE) selects the action within it:

- **Tables** (`TableConfig`) — relational + PostGIS geometry columns, primary keys, indexes.
- **Schemas** (`SchemaConfig`) — create with optional owner, change owner (UPDATE), drop with `RESTRICT` (default) or `CASCADE`.
- **Roles** (`DbRoleConfig`) — database roles/users (a user is a role with `LOGIN`), optional password, and embedded schema-level grants.

PostGIS-specific concepts (geometry columns, SRID, GIST indexes) and all flavor-specific SQL are isolated behind a small `SqlDialect` seam, so a second SQL flavor (MySQL, Oracle, …) can be added later without touching the adapter.

**Status:** Tables — CREATE/DELETE; Schemas — CREATE/UPDATE/DELETE; Roles — CREATE/UPDATE/DELETE. All covered by unit, integration, and end-to-end tests. Table UPDATE returns `UNSUPPORTED_OPERATION` (deferred).

For the full design rationale (idempotency policy, why no migration tool, future two-module split), see [docs/postgis-adapter-design.md](../docs/postgis-adapter-design.md).

## Architecture

```text
┌──────────────────────────────────────────────┐
│   Kafka Topics                               │
│   - de.civitascore.data.sql.table.*              │
│   - de.civitascore.data.sql.schema.*             │
│   - de.civitascore.data.sql.role.*               │
└──────────────┬───────────────────────────────┘
               │ CloudEvent → ConfigEvent (Table/Schema/DbRole payload)
               ↓
┌──────────────────────────────────────────────┐
│   PostgisAdapter (routes by payload type)    │
│   - SqlDialect renders DDL                    │
│   - GrantReconciler diffs role grants         │
│   - CredentialDecryptor for ENC(...) passwords│
│   - HikariCP pool, JDBC transaction per event │
│   - SQLState-based idempotency                │
└──────────────┬───────────────────────────────┘
               │ JDBC
               ↓
┌──────────────────────────────────────────────┐
│   PostgreSQL / PostGIS                       │
└──────────────────────────────────────────────┘
```

## Supported Operations

| Resource | CREATE | UPDATE | DELETE |
|----------|--------|--------|--------|
| Table  | ✅ | ❌ `UNSUPPORTED_OPERATION` (deferred) | ✅ |
| Schema | ✅ (optional `AUTHORIZATION` owner) | ✅ (owner change) | ✅ (`RESTRICT`, or `CASCADE` when `cascade=true`) |
| Role   | ✅ (attributes, password, grants) | ✅ (attributes, password, **grant reconcile**) | ✅ |

Idempotency on conflict (absorbed as success):

| Operation | SQLState | Meaning |
|-----------|----------|---------|
| CREATE | `42P07` / `42P06` / `42710` | duplicate table / schema / role |
| DELETE | `42P01` / `3F000` / `42704` | undefined table / invalid schema / undefined role |

**Grant reconciliation (role UPDATE):** the payload is the desired state. The adapter reads the role's current schema privileges (including their grant-option state) from the database, `GRANT`s those newly present, and `REVOKE`s those no longer listed. The grant option is reconciled too: a held privilege is re-granted `WITH GRANT OPTION` when the payload requests it, and downgraded via `REVOKE GRANT OPTION FOR …` when it no longer does. `ALL` expands to `USAGE` + `CREATE` for comparison.

## Subscribed Topics

| Topic Constant | Topic Value |
|----------------|-------------|
| `SQL_TABLE_CREATED` | `de.civitascore.data.sql.table.created` |
| `SQL_TABLE_DELETED` | `de.civitascore.data.sql.table.deleted` |
| `SQL_SCHEMA_CREATED` | `de.civitascore.data.sql.schema.created` |
| `SQL_SCHEMA_UPDATED` | `de.civitascore.data.sql.schema.updated` |
| `SQL_SCHEMA_DELETED` | `de.civitascore.data.sql.schema.deleted` |
| `SQL_ROLE_CREATED` | `de.civitascore.data.sql.role.created` |
| `SQL_ROLE_UPDATED` | `de.civitascore.data.sql.role.updated` |
| `SQL_ROLE_DELETED` | `de.civitascore.data.sql.role.deleted` |

## Configuration

```properties
# Topics to subscribe to (comma-separated).
# Table UPDATE is not supported (no in-place ALTER TABLE), so table.updated is not subscribed.
postgis.topics=de.civitascore.data.sql.table.created,de.civitascore.data.sql.table.deleted,\
  de.civitascore.data.sql.schema.created,de.civitascore.data.sql.schema.updated,de.civitascore.data.sql.schema.deleted,\
  de.civitascore.data.sql.role.created,de.civitascore.data.sql.role.updated,de.civitascore.data.sql.role.deleted

# JDBC connection (required)
postgis.jdbc.url=jdbc:postgresql://localhost:5432/civitas
postgis.jdbc.user=civitas
postgis.jdbc.password=civitas

# Optional pool tuning (defaults shown)
postgis.jdbc.maxPoolSize=5
postgis.jdbc.connectionTimeoutMs=5000
```

All properties are overridable via environment variables (`.` → `_`, uppercase): `postgis.jdbc.url` → `POSTGIS_JDBC_URL`.

**Prerequisite:** the PostGIS extension must already be installed in the target database (`CREATE EXTENSION postgis`). The adapter does not install it.

**Role passwords:** the `password` field may be an encrypted `ENC(...)` value following the project credential convention. The adapter decrypts it with the `CIVITAS_MASTER_KEY` master key (see [`CredentialDecryptor`](../config-adapter-api/src/main/java/de/civitascore/configadapter/crypto/CredentialDecryptor.java), credential context `portal-backend:sql-role`). If `CIVITAS_MASTER_KEY` is unset and an encrypted password arrives, the event fails fatally. Plaintext passwords are accepted as-is. Passwords are never written to logs or error messages (`PASSWORD '…'` literals are redacted).

**Startup behaviour:** the HikariCP pool starts eagerly but skips the fail-fast initial connection attempt (`initializationFailTimeout = -1`) — an unreachable database never fails adapter startup. If the DB is unreachable when an event arrives, the failure is reported as a `RetryableAdapterException` rather than crashing the adapter at boot.

## Event Payload

The payload uses the sealed `PostgisConfigValue` hierarchy with three variants — `TableConfig` (`"resourceType": "sql-table"`), `SchemaConfig` (`"sql-schema"`), and `DbRoleConfig` (`"sql-role"`). The example below shows a table payload.

```json
{
  "metadata": { "messageId": "msg-1", "correlationId": "corr-1", "resultTopic": "result-topic", "..." : "..." },
  "payload": {
    "targetComponent": "postgis",
    "targetResource": "iot/sensor_readings",
    "operation": "CREATE",
    "config": {
      "path": "iot/sensor_readings",
      "value": {
        "resourceType": "sql-table",
        "schema": "iot",
        "name": "sensor_readings",
        "columns": [
          { "name": "id",          "type": "BIGINT",      "nullable": false },
          { "name": "recorded_at", "type": "TIMESTAMPTZ", "nullable": false },
          { "name": "temperature", "type": "NUMERIC", "precision": 6, "scale": 2, "nullable": true }
        ],
        "geometryColumns": [
          { "name": "location", "geometryType": "POINT", "srid": 4326, "nullable": false }
        ],
        "primaryKey": ["id"],
        "indexes": [
          { "name": "idx_sensor_readings_location", "columns": ["location"], "method": "GIST" }
        ]
      }
    }
  }
}
```

Generated DDL for the example above:

```sql
CREATE SCHEMA "iot";
CREATE TABLE "iot"."sensor_readings" (
  "id" BIGINT NOT NULL,
  "recorded_at" TIMESTAMPTZ NOT NULL,
  "temperature" NUMERIC(6, 2),
  "location" GEOMETRY(POINT, 4326) NOT NULL,
  PRIMARY KEY ("id")
);
CREATE INDEX "idx_sensor_readings_location" ON "iot"."sensor_readings" USING GIST ("location");
```

### Schema payload (`resourceType: sql-schema`)

```json
{
  "resourceType": "sql-schema",
  "name": "iot",
  "owner": "iot_admin",
  "cascade": false
}
```

`CREATE` → `CREATE SCHEMA "iot" AUTHORIZATION "iot_admin"`; `UPDATE` → `ALTER SCHEMA "iot" OWNER TO "iot_admin"`; `DELETE` → `DROP SCHEMA "iot" RESTRICT` (or `CASCADE` when `cascade=true`).

### Role payload (`resourceType: sql-role`)

```json
{
  "resourceType": "sql-role",
  "name": "analyst",
  "canLogin": true,
  "password": "ENC(BASE64-AES-GCM-PAYLOAD)",
  "inherit": true,
  "grants": [
    { "schema": "iot", "privileges": ["USAGE", "CREATE"], "withGrantOption": false }
  ]
}
```

`CREATE` generates `CREATE ROLE "analyst" WITH LOGIN PASSWORD '…' INHERIT` followed by `GRANT USAGE, CREATE ON SCHEMA "iot" TO "analyst"`. On `UPDATE`, the grants are reconciled against the live database (see *Grant reconciliation* above). `DELETE` → `DROP OWNED BY "analyst"` + `DROP ROLE "analyst"` (the `DROP OWNED BY` revokes the role's remaining privileges first — PostgreSQL refuses to drop a role that still holds grants).

## Supported Column Types

Generic (`ColumnType` enum): `SMALLINT`, `INTEGER`, `BIGINT`, `NUMERIC` (with optional `precision`/`scale`), `REAL`, `DOUBLE_PRECISION`, `BOOLEAN`, `VARCHAR` (with optional `length`), `TEXT`, `UUID`, `DATE`, `TIME`, `TIMESTAMP`, `TIMESTAMPTZ`, `JSONB`, `BYTEA`.

Geometry (`GeometryType` enum): `POINT`, `LINESTRING`, `POLYGON`, `MULTIPOINT`, `MULTILINESTRING`, `MULTIPOLYGON`, `GEOMETRYCOLLECTION`, `GEOMETRY`. Optional `dimension` of `3` appends `Z`, `4` appends `ZM` to the rendered type (e.g. `GEOMETRY(POINTZ, 4326)`).

Index methods (`IndexConfig.IndexMethod`): `BTREE` (default), `GIST` (for geometry), `GIN`.

Schema privileges (`SchemaPrivilege` enum): `USAGE`, `CREATE`, `ALL` (`ALL` expands to `USAGE` + `CREATE`).

## Error Handling

| Condition | Outcome |
|-----------|---------|
| CREATE with `42P07` / `42P06` / `42710` (duplicate table / schema / role) | success (idempotent) |
| DELETE with `42P01` / `3F000` / `42704` (undefined table / invalid schema / undefined role) | success (idempotent) |
| `SQLState 08*` (connection failure) | `RetryableAdapterException` (`POSTGIS_CONNECTION_ERROR`) |
| Other `SQLException` | `FatalAdapterException` (`POSTGIS_DDL_ERROR`) — rolled back |
| Encrypted password but `CIVITAS_MASTER_KEY` unset / decryption fails | `FatalAdapterException` (`POSTGIS_ERROR`) |
| Table `UPDATE` operation | `FatalAdapterException` (`UNSUPPORTED_OPERATION`) — deferred |
| Payload not a table / schema / role | `FatalAdapterException` (`INVALID_PAYLOAD`) |

All DDL for a single event runs in one JDBC transaction; on failure the transaction is rolled back before the exception propagates.

## Saga Participation

Besides the event-driven `PostgisAdapter`, the module ships a `PostgisSagaHandler` — a `SagaCommandHandler` (ServiceLoader-registered under `META-INF/services/de.civitascore.configadapter.adapter.SagaCommandHandler`) discovered by the application and registered in the saga orchestrator (Flowable or custom). It lets PostGIS participate as a compensable step in any saga, mirroring the FROST / APISIX / NiFi handlers but executing DDL over JDBC instead of HTTP.

Forward operations and their compensations:

| Forward (`EXECUTE_STEP`) | Payload field (with `resourceType`) | Compensation (`COMPENSATE_STEP`) | Compensation payload |
|--------------------------|--------------------------------------|----------------------------------|----------------------|
| `CREATE_TABLE`  | `tableConfig` (`TableConfig`)   | `DROP_TABLE`  | `{schema?, table}` |
| `CREATE_SCHEMA` | `schemaConfig` (`SchemaConfig`) | `DROP_SCHEMA` | `{schema, cascade?}` |
| `CREATE_ROLE`   | `roleConfig` (`DbRoleConfig`)   | `DROP_ROLE`   | `{role}` |

- The nested config map must carry its `resourceType` discriminator (`sql-table` / `sql-schema` / `sql-role`), exactly as the config travels inside CloudEvents.
- Each `CREATE_*` returns its identifiers as `compensationData`; the orchestrator flattens them into the payload of the compensating `DROP_*`.
- `CREATE_*` absorbs duplicate-object SQLStates and `DROP_*` absorbs missing-object SQLStates, so steps and compensations are safe to retry.
- `DROP_*` can also be used as a forward step; encrypted role passwords are decrypted exactly as in the event path.

The handler reuses the same `ConnectionProvider`, `PostgisDialect`, and `GrantReconciler` as the adapter (shared via the package-private `SqlDdlSupport`).

### Dataset-saga sink provisioning (`PROVISION_SINK` / `DEPROVISION_SINK`)

Beyond the generic resource ops, the handler is a **step in the dataset sagas**. When the dataset trigger carries a `POSTGIS` data sink (`hasGeoSink`), the CREATE saga runs `PROVISION_SINK` **before** GeoServer registers its datastore — GeoServer publishes feature types from a PostGIS table, and that table must exist first. `PROVISION_SINK` reads each `datasinks[POSTGIS].configuration` and creates the schema (optional), table, and a GeoServer read role + grant, all in one transaction (idempotent). `DEPROVISION_SINK` (DELETE saga / CREATE compensation) drops the table and role; the schema is left (it may be shared).

`datasinks[POSTGIS]` shape — `configuration.columns` is an optional explicit override; when absent, columns are derived from the sink's `dataStructure` (the JSON Schema the backend stores on the data-structure version and ships at publish time):

```json
{ "type": "POSTGIS",
  "configuration": {
    "schema": "ds_42", "owner": "ds_42_admin",
    "tableName": "sensor_readings",
    "columns": [ {"name": "id", "type": "BIGINT", "nullable": false} ],
    "geometryColumns": [ {"name": "geom", "geometryType": "POINT", "srid": 4326} ],
    "primaryKey": ["id"],
    "readRole": {"name": "ds_42_geo", "privileges": ["USAGE"]} },
  "dataStructure": { "$id": "http://civitas.org/model/observation/1.0.0",
    "$schema": "https://json-schema.org/draft/2020-12/schema", "title": "Observation",
    "type": "object",
    "properties": { "station_id": {"type": "string"},
      "location": {"$ref": "https://geojson.org/schema/Point.json"} },
    "required": ["station_id"] } }
```

#### Column derivation from `dataStructure`

`DataStructureTableMapper` reads the root `properties`, or the schema's property-carrying `$defs`/`definitions` entry (inlined referenced types have no properties and are skipped); `required` properties become `NOT NULL`.

| JSON Schema type | Column type |
|---|---|
| `integer` | `BIGINT` |
| `number` | `DOUBLE_PRECISION` |
| `boolean` | `BOOLEAN` |
| `string(date-time)` | `TIMESTAMPTZ` |
| `string(date)` / `string(time)` / `string(uuid)` | `DATE` / `TIME` / `UUID` |
| `object`, `array` | `JSONB` |
| `$ref` to a GeoJSON schema (`https://geojson.org/schema/<Type>.json`) | geometry column, SRID from the property's `crs` (default 4326) |
| other `$ref` (e.g. `#/$defs/<Type>`, nested object) | `JSONB` |
| `string`, unknown | `TEXT` |

Geometry is recognized only by the GeoJSON-host `$ref` — a local `#/$defs/Point` is a nested object, not geometry. The geometry column's CRS comes from an optional `crs` in EPSG form on the property (`{ "$ref": "https://geojson.org/schema/Point.json", "crs": "EPSG:25832" }`), which the adapter maps to the column's SRID, defaulting to EPSG:4326 (matching the GeoServer handler) when absent. Explicit `configuration.geometryColumns` are excluded from derivation. A schema without usable properties fails the step with an actionable error.

Inheritance (`allOf` / parent `$ref`): a definition's columns are gathered by following its `allOf` branches and any node-level local `$ref` (`#/$defs/<Type>`, `#/definitions/<Type>`) to parent definitions, not just the node's own `properties` — so a subclass that carries its fields under `allOf` still yields the full column set. The root is the entry point when it has `properties` or `allOf`; otherwise the single (or title-matching) named definition is resolved the same way. Inherited columns come first; on a name collision the most specific (subclass) definition wins the column type while the column keeps its first-seen position, and each definition's `required` entries are unioned into the `NOT NULL` set. A node-level parent `$ref` that resolves to no definition is rejected (it would otherwise drop inherited columns and create a partial table); a property-level `$ref` stays `JSONB` and is never followed as a parent.

Saga placement — CREATE: `… APISIX → [hasGeoSink] PROVISION_SINK → GeoServer workspace → datastore → layers → …`; DELETE: `… GeoServer DELETE_WORKSPACE → DEPROVISION_SINK → FROST`. UPDATE does not re-provision the sink.

## Tests

```bash
# Unit tests for this module
mvn test -pl config-adapter-postgis

# Single class
mvn test -pl config-adapter-postgis -Dtest=PostgisDialectTest

# Single method
mvn test -pl config-adapter-postgis -Dtest=PostgisAdapterTest#duplicateTableSqlStateIsAbsorbedAsSuccess

# Unit + integration + end-to-end tests (requires Docker)
mvn -pl config-adapter-postgis -am verify
```

**Coverage:**

- **93 unit tests** — dialect DDL rendering (tables, schemas, roles, grants), SQLState classification, grant-reconcile diffing (`GrantReconcilerTest`), adapter behaviour with mocked JDBC (`PostgisAdapterTest`, incl. credential handling and grant reconciliation), and the saga handler with mocked JDBC (`PostgisSagaHandlerTest` — forward ops, compensations, idempotency, credential failure).
- **22 integration tests** against a real `postgis/postgis:16-3.4-alpine` container:
  - `PostgisAdapterIT` (16) — generated DDL accepted by Postgres and objects physically exist: table create (geometry + SRID + GIST index), schema create/drop (owner, `CASCADE` vs `RESTRICT`), role create with login + password + grants, role UPDATE grant reconciliation, role delete, idempotency, invalid DDL → fatal, unreachable JDBC → retryable.
  - `PostgisSagaHandlerIT` (4) — saga forward op then compensation against the DB (table, schema, role+grant), and idempotent compensation of a missing object.
  - `PostgisEndToEndIT` (2) — full pipeline through Kafka + `KafkaEventHandler` + adapter + PostGIS, verifying both the result event and the database state.

The PostGIS container is shared across IT classes via the singleton pattern in `AbstractPostgisIT`; Kafka is per-class.
