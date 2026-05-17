# PostGIS Config Adapter — Design Sketch

> **Module**: `config-adapter-postgis`
> **Scope (first step)**: table create / update / delete via JDBC
> **Status**: scaffolded — CREATE and DELETE implemented and unit-tested; UPDATE returns `UNSUPPORTED_OPERATION` (deferred). No integration test yet.

---

## 1. Goals

- Apply DDL changes to a PostgreSQL/PostGIS database in response to `ConfigEvent`s.
- Operations implemented in this scaffold: **table CREATE and DELETE** (UPDATE deferred — see Status above).
- Keep PostGIS specifics (geometry types, SRID, GIST indexes) isolated from a generic SQL core, so future SQL flavors (MySQL, Oracle, …) can be added by introducing a sibling dialect rather than rewriting the adapter.
- Idempotency behaviour identical to the existing adapters (FROST, APISIX, Keycloak).

---

## 2. Module Layout

Single module today; the dialect seam allows graduation to a `config-adapter-sql-base` + `config-adapter-postgis` pair the day a second flavor lands.

```
config-adapter-api/
└── src/main/java/de/civitascore/configadapter/model/postgis/
    ├── PostgisConfigValue.java       ← sealed, permits TableConfig (+ future types)
    ├── TableConfig.java              ← class (Jackson POJO with setters)
    ├── ColumnConfig.java             ← record(name, type, length, precision, scale,
    │                                            nullable, defaultExpr)
    ├── ColumnType.java               ← enum: SMALLINT, INTEGER, BIGINT, NUMERIC, REAL,
    │                                          DOUBLE_PRECISION, BOOLEAN, VARCHAR, TEXT,
    │                                          UUID, DATE, TIME, TIMESTAMP, TIMESTAMPTZ,
    │                                          JSONB, BYTEA
    ├── GeometryColumnConfig.java     ← record(name, geometryType, srid, dimension, nullable)
    ├── GeometryType.java             ← enum: POINT, LINESTRING, POLYGON,
    │                                          MULTIPOINT, MULTILINESTRING,
    │                                          MULTIPOLYGON, GEOMETRYCOLLECTION,
    │                                          GEOMETRY
    └── IndexConfig.java              ← record(name, columns, unique, method)
                                              method: BTREE | GIST | GIN

config-adapter-postgis/                ← module
├── pom.xml                            ← deps: api, postgresql JDBC, HikariCP
├── src/main/resources/META-INF/services/
│   └── de.civitascore.configadapter.adapter.ConfigAdapter
│         → de.civitascore.configadapter.postgis.PostgisAdapter
└── src/main/java/de/civitascore/configadapter/postgis/
    ├── PostgisAdapter.java           ← extends AbstractConfigAdapter;
    │                                    SQLState classification lives here
    ├── ConnectionProvider.java       ← HikariDataSource wrapper
    ├── dialect/
    │   ├── SqlDialect.java           ← seam for future flavors
    │   └── PostgisDialect.java       ← PostGIS implementation (DDL + isDuplicate/
    │                                    isMissing/isConnectivity)
    └── ddl/
        └── TableDdlBuilder.java      ← thin wrapper that delegates to the dialect
```

### Sealed payload type

```java
public sealed interface PostgisConfigValue extends ConfigValue permits TableConfig {
  String POSTGIS_RESULT_TYPE = "de.civitascore.data.table.processing.result";
}

// TableConfig is a regular Jackson-deserialisable class (POJO with getters/setters),
// to stay consistent with the existing IDM/APISIX/FROST model style.
public final class TableConfig implements PostgisConfigValue {
  private String schema;
  private String name;
  private List<ColumnConfig> columns;            // generic typed columns
  private List<GeometryColumnConfig> geometryColumns;   // PostGIS-only; empty = no PostGIS deps
  private List<String> primaryKey;
  private List<IndexConfig> indexes;
  // ... getters / setters / equals / hashCode / qualifiedName()
}
```

When `geometryColumns` is empty, the generated SQL contains no PostGIS-specific tokens — the model layer stays clean for non-PostGIS flavors later.

### Dialect seam

```java
public interface SqlDialect {
    String quoteIdent(String ident);
    String renderColumn(ColumnConfig column);
    String renderGeometryColumn(GeometryColumnConfig column);
    String generatedIndexName(TableConfig table, IndexConfig index);
    List<String> createTable(TableConfig table);
    List<String> dropTable(String schema, String name);
    boolean isDuplicate(SQLException e);       // → idempotent CREATE
    boolean isMissing(SQLException e);         // → idempotent DELETE
    boolean isConnectivity(SQLException e);    // → retryable
}
```

---

## 3. Idempotency Policy

Matches the convention established by FROST, APISIX, and Keycloak: do **not** pre-check, do **not** generate `IF NOT EXISTS`. Let the database fail naturally and absorb the conflict in the adapter — the same way APISIX absorbs HTTP 409 and FROST absorbs 409/500 on CREATE.

### SQLState mapping (PostgreSQL)

| Operation     | SQLState | Meaning            | Outcome              |
|---------------|----------|--------------------|----------------------|
| CREATE TABLE  | `42P07`  | duplicate_table    | success (idempotent) |
| CREATE SCHEMA | `42P06`  | duplicate_schema   | success (idempotent) |
| CREATE INDEX  | `42P07`  | duplicate_object   | success (idempotent) |
| DROP TABLE    | `42P01`  | undefined_table    | success (idempotent) |
| DROP SCHEMA   | `3F000`  | invalid_schema     | success (idempotent) |
| `08xxx`       | —        | connection failure | retryable (2xxx)     |
| `22xxx`       | —        | data exception     | fatal (1xxx)         |
| `23xxx`       | —        | integrity viol.    | fatal (1xxx)         |
| `42xxx` other | —        | syntax / access    | fatal (1xxx)         |

Mapping lives inside `PostgisDialect` (via `isDuplicate` / `isMissing` / `isConnectivity`). A future MySQL dialect would map its own codes (e.g. `1050` "table exists") without touching the adapter.

---

## 4. Event Flow

1. `KafkaEventHandler` consumes the CloudEvent → `CloudEventProcessor` deserialises to `ConfigEvent`.
2. `PostgisAdapter.doProcessConfigEvent()` extracts the `TableConfig` from `event.payload().config().value()`.
3. `TableDdlBuilder` (constructed with the dialect) produces the ordered DDL list:
   - CREATE: `buildCreate(table)` → `CREATE SCHEMA …` (if schema is given), `CREATE TABLE …`, then `CREATE INDEX …` (one per `IndexConfig`)
   - DELETE: `buildDrop(table)` → `DROP TABLE …`
   - UPDATE: currently rejected with `UNSUPPORTED_OPERATION` (deferred)
4. Statements are executed inside a single JDBC transaction. Per statement:
   - success → continue
   - `dialect.isDuplicate` on CREATE → log info, treat as success
   - `dialect.isMissing` on DELETE → log info, treat as success
   - other `SQLException` → rollback, propagate to outer catch
5. Outer catch classifies the failure:
   - `dialect.isConnectivity` → `RetryableAdapterException` (`POSTGIS_CONNECTION_ERROR`)
   - otherwise → `FatalAdapterException` (`POSTGIS_DDL_ERROR`)
6. Publish `ConfigResultEvent` (SUCCESS or FAILURE) to the `resultTopic` from event metadata.

---

## 5. Configuration Keys

All overridable via env vars (`.` → `_`, uppercase).

```
postgis.topics                    (required — comma-separated list of subscribed topics)
postgis.jdbc.url                  (required)
postgis.jdbc.user                 (required)
postgis.jdbc.password             (default: empty)
postgis.jdbc.maxPoolSize          (default: 5)
postgis.jdbc.connectionTimeoutMs  (default: 5000)
```

> The PostGIS extension itself (`CREATE EXTENSION postgis`) is treated as an operator-provisioned prerequisite of the target database, not as part of the adapter's per-event DDL. A `postgis.ddl.createExtensionIfMissing` bootstrap step was considered but deferred — when added, it becomes the only place where `IF NOT EXISTS` is used by the adapter.

---

## 6. Testing

- **Unit** (`*Test.java`) — implemented (46 tests):
  - `PostgisDialectTest` — DDL rendering (schema, PK, VARCHAR length, NUMERIC precision/scale, NOT NULL, default expression, geometry+SRID, geometry dimension 3, GIST/BTREE indexes, generated index names) and SQLState classification (`isDuplicate`, `isMissing`, `isConnectivity`).
  - `PostgisAdapterTest` — mocks `ConnectionProvider`/`Connection`/`Statement`; covers CREATE/DELETE happy paths, duplicate/missing absorption, connectivity → retryable, other SQLState → fatal, rollback on failure, `UPDATE` → `UNSUPPORTED_OPERATION`, wrong payload type → `INVALID_PAYLOAD`, resource cleanup.
- **Integration** (`*IT.java`) — deferred. Planned: Testcontainers `postgis/postgis:16-3.4`, real `DataSource`, end-to-end CREATE/DROP including:
  - geometry column with non-default SRID and dimension
  - GIST index on a geometry column
  - duplicate-table absorption
  - missing-table absorption on DROP
  - connectivity failure → retryable

---

## 7. Future Two-Module Split (deferred)

When a second SQL flavor is needed, move the following to a new `config-adapter-sql-base` module:

- `SqlDialect`
- `ColumnType`, `IndexConfig`, generic `ColumnConfig`
- `ConnectionProvider`, `TableDdlBuilder`
- shared `*ErrorHandler` skeleton

`config-adapter-postgis` keeps `PostgisDialect`, `GeometryColumnConfig`, `GeometryType`, and the SPI registration. No call-site changes.

---

## 8. Considered Alternative — Migration Tools (Flyway / Liquibase)

A schema-migration tool was considered as the DDL execution layer. **Decision: not used.** Rationale and findings below for future reference.

### What was considered

- **Flyway** — SQL-first migrations, history table (`flyway_schema_history`), programmatic API (`Flyway.configure().dataSource(…).load().migrate()`). Each event would translate to a versioned migration (`V{hash}__create_{schema}_{table}.sql`).
- **Liquibase** — XML / YAML / JSON changelogs, dialect-aware changeSets, built-in PostGIS support via extensions. Each event would produce a changeSet with a deterministic `id` and `author`.

### Where it appealed

- Idempotency for free: the history table makes re-applying the same migration a no-op, matching the desired "re-deliver event = success" behaviour.
- A built-in audit trail of schema changes inside the database.
- Liquibase's dialect-aware changelogs would align with the planned future multi-flavor split.

### Why it was rejected

1. **Model mismatch.** Migration tools are built for *versioned application-schema evolution* baked at build time, not *event-driven adhoc DDL* arriving from Kafka at runtime. The strengths of these tools (resumable migrations, baseline + diff, build-time validation) do not apply.
2. **Ordering friction.** Flyway expects monotonically increasing migration versions. Out-of-order events would require `outOfOrder=true`, which silently disables the validation that justifies using Flyway in the first place.
3. **Idempotency is already cheap.** SQLState absorption is ~10 lines inside the dialect, matches the FROST/APISIX/Keycloak convention exactly, and keeps idempotency a property of the adapter rather than of an external history table.
4. **Audit-log argument is weak in this codebase.** Result events on the Kafka result topic already provide the audit trail. A second source of truth (the migration history table) adds drift risk.
5. **Multi-flavor argument is premature.** The dialect interface gives us the same extension point with one less dependency; the migration tool would only earn its place if we already had to support multiple SQL flavors, which we explicitly deferred.
6. **Operational footprint.** Migration tools add a runtime dependency, a managed table in the customer's DB, and a class of failure modes (checksum mismatches, baseline drift) that the project does not currently have to reason about.

### Net

Plain JDBC + SQLState mapping inside the dialect is simpler, idiomatic to this codebase, and loses no capability we actually need at this stage. The decision is reversible: if event-driven DDL grows into something more change-management-heavy, the dialect seam is also where a migration-tool integration would slot in.
