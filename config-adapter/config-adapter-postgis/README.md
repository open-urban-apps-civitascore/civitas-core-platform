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

```
┌──────────────────────────────────────────────┐
│   Kafka Topics                               │
│   - de.civitascore.data.table.*              │
│   - de.civitascore.data.schema.*             │
│   - de.civitascore.data.role.*               │
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

**Grant reconciliation (role UPDATE):** the payload is the desired state. The adapter reads the role's current schema privileges from the database, `GRANT`s those newly present, and `REVOKE`s those no longer listed. `ALL` expands to `USAGE` + `CREATE` for comparison.

## Subscribed Topics

| Topic Constant | Topic Value |
|----------------|-------------|
| `TABLE_CREATED` | `de.civitascore.data.table.created` |
| `TABLE_UPDATED` | `de.civitascore.data.table.updated` |
| `TABLE_DELETED` | `de.civitascore.data.table.deleted` |
| `SCHEMA_CREATED` | `de.civitascore.data.schema.created` |
| `SCHEMA_UPDATED` | `de.civitascore.data.schema.updated` |
| `SCHEMA_DELETED` | `de.civitascore.data.schema.deleted` |
| `DB_ROLE_CREATED` | `de.civitascore.data.role.created` |
| `DB_ROLE_UPDATED` | `de.civitascore.data.role.updated` |
| `DB_ROLE_DELETED` | `de.civitascore.data.role.deleted` |

## Configuration

```properties
# Topics to subscribe to (comma-separated)
postgis.topics=de.civitascore.data.table.created,de.civitascore.data.table.updated,de.civitascore.data.table.deleted,\
  de.civitascore.data.schema.created,de.civitascore.data.schema.updated,de.civitascore.data.schema.deleted,\
  de.civitascore.data.role.created,de.civitascore.data.role.updated,de.civitascore.data.role.deleted

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

**Role passwords:** the `password` field may be an encrypted `ENC(...)` value following the project credential convention. The adapter decrypts it with the `CIVITAS_MASTER_KEY` master key (see [`CredentialDecryptor`](../config-adapter-api/src/main/java/de/civitascore/configadapter/crypto/CredentialDecryptor.java), credential context `portal-backend:postgis-role`). If `CIVITAS_MASTER_KEY` is unset and an encrypted password arrives, the event fails fatally. Plaintext passwords are accepted as-is. Passwords are never written to logs or error messages (`PASSWORD '…'` literals are redacted).

**Startup behaviour:** the HikariCP pool is created lazily (`initializationFailTimeout = -1`) — adapter startup does not probe the database. If the DB is unreachable when an event arrives, the failure is reported as a `RetryableAdapterException` rather than crashing the adapter at boot.

## Event Payload

The payload uses the sealed `PostgisConfigValue` hierarchy, currently with one variant — `TableConfig`. JSON discriminator: `"resourceType": "postgis-table"`.

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
        "resourceType": "postgis-table",
        "schema": "iot",
        "name": "sensor_readings",
        "columns": [
          { "name": "id",          "type": "BIGINT",      "nullable": false },
          { "name": "recorded_at", "type": "TIMESTAMPTZ", "nullable": false, "defaultExpr": "now()" },
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
  "recorded_at" TIMESTAMPTZ NOT NULL DEFAULT now(),
  "temperature" NUMERIC(6, 2),
  "location" GEOMETRY(POINT, 4326) NOT NULL,
  PRIMARY KEY ("id")
);
CREATE INDEX "idx_sensor_readings_location" ON "iot"."sensor_readings" USING GIST ("location");
```

### Schema payload (`resourceType: postgis-schema`)

```json
{
  "resourceType": "postgis-schema",
  "name": "iot",
  "owner": "iot_admin",
  "cascade": false
}
```

`CREATE` → `CREATE SCHEMA "iot" AUTHORIZATION "iot_admin"`; `UPDATE` → `ALTER SCHEMA "iot" OWNER TO "iot_admin"`; `DELETE` → `DROP SCHEMA "iot" RESTRICT` (or `CASCADE` when `cascade=true`).

### Role payload (`resourceType: postgis-role`)

```json
{
  "resourceType": "postgis-role",
  "name": "analyst",
  "canLogin": true,
  "password": "ENC(BASE64-AES-GCM-PAYLOAD)",
  "inherit": true,
  "grants": [
    { "schema": "iot", "privileges": ["USAGE", "CREATE"], "withGrantOption": false }
  ]
}
```

`CREATE` generates `CREATE ROLE "analyst" WITH LOGIN PASSWORD '…' INHERIT` followed by `GRANT USAGE, CREATE ON SCHEMA "iot" TO "analyst"`. On `UPDATE`, the grants are reconciled against the live database (see *Grant reconciliation* above). `DELETE` → `DROP ROLE "analyst"`.

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

- **82 unit tests** — dialect DDL rendering (tables, schemas, roles, grants), SQLState classification, grant-reconcile diffing (`GrantReconcilerTest`), and adapter behaviour with mocked JDBC (including credential handling and grant reconciliation).
- **16 integration tests** (`PostgisAdapterIT`) — real `postgis/postgis:16-3.4-alpine` container. Asserts generated DDL is accepted by Postgres and objects physically exist. Covers table create (geometry + SRID + GIST index), schema create/drop (owner, `CASCADE` vs `RESTRICT`), role create with login + password + grants, role UPDATE grant reconciliation (revoke removed privilege), role delete, idempotency on duplicate/missing, invalid DDL → fatal, unreachable JDBC → retryable.
- **2 end-to-end tests** (`PostgisEndToEndIT`) — full pipeline through Kafka + `KafkaEventHandler` + adapter + PostGIS, verifying both the result event on the Kafka result topic and the database state.

The PostGIS container is shared across IT classes via the singleton pattern in `AbstractPostgisIT`; Kafka is per-class.
