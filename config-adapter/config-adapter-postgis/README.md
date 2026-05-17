# PostGIS Config Adapter

Adapter for managing PostgreSQL/PostGIS **table** configuration via DDL, driven by CloudEvents on Kafka.

## Overview

`PostgisAdapter` applies table-shape changes (CREATE, DELETE) to a target Postgres database. PostGIS-specific concepts (geometry columns, SRID, GIST indexes) are isolated in a small `SqlDialect` seam so a second SQL flavor (MySQL, Oracle, …) can be added later without touching the adapter.

**Status:** scaffolded — CREATE and DELETE implemented and unit-tested; UPDATE returns `UNSUPPORTED_OPERATION` (deferred). No integration test yet.

For the full design rationale (idempotency policy, why no migration tool, future two-module split), see [docs/postgis-adapter-design.md](../docs/postgis-adapter-design.md).

## Architecture

```
┌─────────────────────────────────────┐
│   Kafka Topics                      │
│   - de.civitascore.data.table.*     │
└──────────────┬──────────────────────┘
               │ CloudEvent → ConfigEvent (TableConfig payload)
               ↓
┌─────────────────────────────────────┐
│   PostgisAdapter                    │
│   - TableDdlBuilder (uses dialect)  │
│   - HikariCP-backed ConnectionPool  │
│   - JDBC transaction per event      │
│   - SQLState-based idempotency      │
└──────────────┬──────────────────────┘
               │ JDBC
               ↓
┌─────────────────────────────────────┐
│   PostgreSQL / PostGIS              │
└─────────────────────────────────────┘
```

## Supported Operations

| Operation | Status | Idempotency on conflict |
|-----------|--------|-------------------------|
| CREATE    | ✅     | SQLState `42P07`/`42P06` (duplicate table/schema) → success |
| DELETE    | ✅     | SQLState `42P01`/`3F000` (undefined table / invalid schema) → success |
| UPDATE    | ❌ (deferred) | — (returns `UNSUPPORTED_OPERATION`) |

## Subscribed Topics

| Topic Constant | Topic Value |
|----------------|-------------|
| `TABLE_CREATED` | `de.civitascore.data.table.created` |
| `TABLE_UPDATED` | `de.civitascore.data.table.updated` |
| `TABLE_DELETED` | `de.civitascore.data.table.deleted` |

## Configuration

```properties
# Topics to subscribe to (comma-separated)
postgis.topics=de.civitascore.data.table.created,de.civitascore.data.table.updated,de.civitascore.data.table.deleted

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

## Supported Column Types

Generic (`ColumnType` enum): `SMALLINT`, `INTEGER`, `BIGINT`, `NUMERIC` (with optional `precision`/`scale`), `REAL`, `DOUBLE_PRECISION`, `BOOLEAN`, `VARCHAR` (with optional `length`), `TEXT`, `UUID`, `DATE`, `TIME`, `TIMESTAMP`, `TIMESTAMPTZ`, `JSONB`, `BYTEA`.

Geometry (`GeometryType` enum): `POINT`, `LINESTRING`, `POLYGON`, `MULTIPOINT`, `MULTILINESTRING`, `MULTIPOLYGON`, `GEOMETRYCOLLECTION`, `GEOMETRY`. Optional `dimension` of `3` appends `Z`, `4` appends `ZM` to the rendered type (e.g. `GEOMETRY(POINTZ, 4326)`).

Index methods (`IndexConfig.IndexMethod`): `BTREE` (default), `GIST` (for geometry), `GIN`.

## Error Handling

| Condition | Outcome |
|-----------|---------|
| CREATE with `42P07` (duplicate_table) | success (idempotent) |
| CREATE with `42P06` (duplicate_schema) | success (idempotent) |
| DELETE with `42P01` (undefined_table) | success (idempotent) |
| DELETE with `3F000` (invalid_schema) | success (idempotent) |
| `SQLState 08*` (connection failure) | `RetryableAdapterException` (`POSTGIS_CONNECTION_ERROR`) |
| Other `SQLException` | `FatalAdapterException` (`POSTGIS_DDL_ERROR`) — rolled back |
| `UPDATE` operation | `FatalAdapterException` (`UNSUPPORTED_OPERATION`) — deferred |
| Non-`TableConfig` payload | `FatalAdapterException` (`INVALID_PAYLOAD`) |

All DDL for a single event runs in one JDBC transaction; on failure the transaction is rolled back before the exception propagates.

## Tests

```bash
# Unit tests for this module
mvn test -pl config-adapter-postgis

# Single class
mvn test -pl config-adapter-postgis -Dtest=PostgisDialectTest

# Single method
mvn test -pl config-adapter-postgis -Dtest=PostgisAdapterTest#duplicateTableSqlStateIsAbsorbedAsSuccess
```

46 unit tests cover dialect DDL rendering, SQLState classification, and adapter behaviour with mocked JDBC. Integration tests (Testcontainers + `postgis/postgis:16-3.4`) are planned but not yet implemented.
