# PostGIS Config Adapter — Design Sketch

> **Module**: `config-adapter-postgis`
> **Scope**: table, schema, and role (incl. schema-level grants) CRUD via JDBC
> **Status**: Tables — CREATE/DELETE (UPDATE deferred → `UNSUPPORTED_OPERATION`); Schemas — CREATE/UPDATE/DELETE; Roles — CREATE/UPDATE/DELETE with grant reconciliation. All implemented with unit, integration, and end-to-end tests.

---

## 1. Goals

- Apply DDL changes to a PostgreSQL/PostGIS database in response to `ConfigEvent`s.
- Resources: **tables** (CREATE/DELETE), **schemas** (CREATE/UPDATE/DELETE), and **roles** (CREATE/UPDATE/DELETE) with embedded schema-level grants.
- Keep PostGIS specifics (geometry types, SRID, GIST indexes) and all flavor-specific SQL isolated behind a generic `SqlDialect`, so future SQL flavors (MySQL, Oracle, …) can be added by introducing a sibling dialect rather than rewriting the adapter.
- Idempotency behaviour identical to the existing adapters (FROST, APISIX, Keycloak).
- Role passwords reuse the project credential convention: encrypted `ENC(...)` values decrypted with `CIVITAS_MASTER_KEY` via `CredentialDecryptor`, exactly as the RedPanda adapter handles datasource credentials.

---

## 2. Module Layout

Single module today; the dialect seam allows graduation to a `config-adapter-sql-base` + `config-adapter-postgis` pair the day a second flavor lands.

```
config-adapter-api/
└── src/main/java/de/civitascore/configadapter/model/postgis/
    ├── PostgisConfigValue.java       ← sealed, permits TableConfig, SchemaConfig, DbRoleConfig
    ├── SchemaConfig.java             ← class (name, optional owner, cascade flag)
    ├── DbRoleConfig.java             ← class (name, canLogin, password, attributes, grants)
    ├── SchemaGrant.java              ← record(schema, privileges, withGrantOption)
    ├── SchemaPrivilege.java          ← enum: USAGE, CREATE, ALL
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
    ├── PostgisAdapter.java           ← extends AbstractConfigAdapter; routes by payload
    │                                    type; credential decrypt; SQLState classification
    ├── ConnectionProvider.java       ← HikariDataSource wrapper
    ├── dialect/
    │   ├── SqlDialect.java           ← seam for future flavors (table/schema/role/grant DDL)
    │   └── PostgisDialect.java       ← PostGIS implementation (DDL + isDuplicate/
    │                                    isMissing/isConnectivity)
    └── ddl/
        ├── TableDdlBuilder.java      ← thin wrapper that delegates table DDL to the dialect
        └── GrantReconciler.java      ← pure diff: desired vs. current schema grants
```

### Sealed payload type

```java
public sealed interface PostgisConfigValue extends ConfigValue
    permits TableConfig, SchemaConfig, DbRoleConfig {
  String POSTGIS_RESULT_TYPE = "de.civitascore.data.sql.processing.result";
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
    // tables
    List<String> createTable(TableConfig table);
    List<String> dropTable(String schema, String name);
    // schemas
    List<String> createSchema(SchemaConfig schema);
    List<String> alterSchemaOwner(SchemaConfig schema);
    List<String> dropSchema(SchemaConfig schema);
    // roles
    List<String> createRole(DbRoleConfig role, String decryptedPassword);
    List<String> alterRole(DbRoleConfig role, String decryptedPassword);
    List<String> dropRole(DbRoleConfig role);
    // schema-level grants
    String grantOnSchema(String role, String schema, List<SchemaPrivilege> privs, boolean grantOption);
    String revokeOnSchema(String role, String schema, List<SchemaPrivilege> privs);
    String readSchemaGrantsQuery();            // one ? bind (role) → (schema, privilege) rows
    // classification
    boolean isDuplicate(SQLException e);        // → idempotent CREATE (incl. 42710 role exists)
    boolean isMissing(SQLException e);          // → idempotent DELETE (incl. 42704 role missing)
    boolean isConnectivity(SQLException e);     // → retryable
}
```

### Schemas, roles, and grants

- **Schema** — CREATE renders `CREATE SCHEMA "x" [AUTHORIZATION "owner"]`; UPDATE renders `ALTER SCHEMA "x" OWNER TO "owner"` (no-op when no owner given); DELETE renders `DROP SCHEMA "x" RESTRICT` (default) or `CASCADE` when `cascade=true` on the payload.
- **Role** — a database *user* is just a role with `LOGIN`, so one `DbRoleConfig` models both via `canLogin`. CREATE/ALTER render the attribute clause (`LOGIN`/`NOLOGIN`, `SUPERUSER`, `CREATEDB`, `CREATEROLE`, `INHERIT`) and, when a password is present, `PASSWORD '…'`.
- **Grants** — embedded in `DbRoleConfig.grants` and treated as the desired state. On CREATE every listed grant is issued. On UPDATE the adapter reads the role's current schema privileges (`readSchemaGrantsQuery`), and `GrantReconciler` computes the `GRANT`/`REVOKE` delta — privileges newly present are granted, privileges no longer listed are revoked. `SchemaPrivilege.ALL` expands to `USAGE` + `CREATE` for comparison.

### Credentials

Role passwords follow the project convention (same as the RedPanda adapter). A `password` field may be an `ENC(...)` value; the adapter loads `CIVITAS_MASTER_KEY` via `CryptoKeyLoader.loadAndStretchKeyFromEnv`, then decrypts with `CredentialDecryptor` under the credential context `portal-backend:sql-role`. If the key is absent and an encrypted password arrives, the event fails fatally (`POSTGIS_ERROR`). Plaintext passwords pass through unchanged. The generated `PASSWORD '…'` literal is redacted from all logs and error messages, and the stretched key is zeroed on `close()`.

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
| CREATE ROLE   | `42710`  | duplicate_object   | success (idempotent) |
| DROP ROLE     | `42704`  | undefined_object   | success (idempotent) |
| `08xxx`       | —        | connection failure | retryable (2xxx)     |
| `22xxx`       | —        | data exception     | fatal (1xxx)         |
| `23xxx`       | —        | integrity viol.    | fatal (1xxx)         |
| `42xxx` other | —        | syntax / access    | fatal (1xxx)         |

Mapping lives inside `PostgisDialect` (via `isDuplicate` / `isMissing` / `isConnectivity`). A future MySQL dialect would map its own codes (e.g. `1050` "table exists") without touching the adapter.

---

## 4. Event Flow

1. `KafkaEventHandler` consumes the CloudEvent → `CloudEventProcessor` deserialises to `ConfigEvent`.
2. `PostgisAdapter.doProcessConfigEvent()` reads `event.payload().config().value()` and routes by type — `TableConfig`, `SchemaConfig`, or `DbRoleConfig` (anything else → `INVALID_PAYLOAD`).
3. The matching handler produces the ordered DDL list:
   - Table CREATE → `CREATE SCHEMA …` (if schema given), `CREATE TABLE …`, `CREATE INDEX …`; Table DELETE → `DROP TABLE …`; Table UPDATE → `UNSUPPORTED_OPERATION` (deferred).
   - Schema CREATE/UPDATE/DELETE → `CREATE`/`ALTER … OWNER TO`/`DROP SCHEMA`.
   - Role CREATE → `CREATE ROLE …` + `GRANT …`; Role UPDATE → read current grants, then `ALTER ROLE …` + reconciled `GRANT`/`REVOKE`; Role DELETE → `DROP ROLE …`. Encrypted passwords are decrypted first.
4. Statements are executed inside a single JDBC transaction. Per statement:
   - success → continue
   - `dialect.isDuplicate` on a CREATE-class op → log info, treat as success
   - `dialect.isMissing` on a DELETE-class op → log info, treat as success
   - other `SQLException` → rollback, propagate to outer catch
5. Outer catch classifies the failure:
   - `dialect.isConnectivity` → `RetryableAdapterException` (`POSTGIS_CONNECTION_ERROR`)
   - otherwise → `FatalAdapterException` (`POSTGIS_DDL_ERROR`)
6. Publish `ConfigResultEvent` (SUCCESS or FAILURE) to the `resultTopic` from event metadata.

---

## 5. Configuration Keys

All overridable via env vars (`.` → `_`, uppercase).

```
postgis.topics                    (required — comma-separated list of subscribed topics:
                                    table.*, schema.*, role.*)
postgis.jdbc.url                  (required)
postgis.jdbc.user                 (required)
postgis.jdbc.password             (default: empty)
postgis.jdbc.maxPoolSize          (default: 5)
postgis.jdbc.connectionTimeoutMs  (default: 5000)
CIVITAS_MASTER_KEY                (env var; required only to decrypt ENC(...) role passwords)
```

> The PostGIS extension itself (`CREATE EXTENSION postgis`) is treated as an operator-provisioned prerequisite of the target database, not as part of the adapter's per-event DDL. A `postgis.ddl.createExtensionIfMissing` bootstrap step was considered but deferred — when added, it becomes the only place where `IF NOT EXISTS` is used by the adapter.

---

## 6. Testing

- **Unit** (`*Test.java`) — implemented (82 tests):
  - `PostgisDialectTest` / `PostgisDialectSchemaRoleTest` — DDL rendering for tables (PK, VARCHAR length, NUMERIC precision/scale, NOT NULL, default expression, geometry+SRID, dimension, GIST/BTREE indexes), schemas (authorization, owner change, RESTRICT/CASCADE), roles (login/nologin, password literal escaping, optional attributes), and schema grants/revokes; SQLState classification (`isDuplicate` incl. `42710`, `isMissing` incl. `42704`, `isConnectivity`).
  - `GrantReconcilerTest` — pure diff: new schema → grant all, removed privilege → revoke, unchanged → no-op, `ALL` expansion, schema dropped from payload → revoke all, `withGrantOption` propagation.
  - `PostgisAdapterTest` — mocks `ConnectionProvider`/`Connection`/`Statement`(+`PreparedStatement`/`ResultSet`); covers table CREATE/DELETE, schema CREATE/UPDATE/DELETE, role CREATE (plaintext password + grants), encrypted-password-without-key → fatal, duplicate/missing absorption, role UPDATE grant reconciliation, connectivity → retryable, other SQLState → fatal + rollback, `UPDATE` table → `UNSUPPORTED_OPERATION`, wrong payload → `INVALID_PAYLOAD`, resource cleanup.
- **Integration** (`*IT.java`, via failsafe, `mvn verify`) — implemented:
  - `PostgisAdapterIT` (16 tests) — real `postgis/postgis:16-3.4-alpine` container shared via the singleton pattern in `AbstractPostgisIT`. Asserts generated DDL is accepted by Postgres and objects physically exist (queried via `information_schema` / `pg_roles` / `has_schema_privilege`). Covers: table CREATE in public + new schema, geometry + SRID + GIST index, duplicate/missing absorption, table DELETE; schema CREATE with owner, DROP `CASCADE`, non-empty `RESTRICT` drop → fatal; role CREATE with login + password + grants, role UPDATE reconcile (revoke removed privilege), role DELETE; invalid DDL → `POSTGIS_DDL_ERROR`; unreachable JDBC → `RetryableAdapterException`.
  - `PostgisSagaHandlerIT` (4 tests) — `PostgisSagaHandler` against the real container: forward op then compensation for table / schema / role+grant, plus idempotent compensation of a missing object.
  - `PostgisEndToEndIT` (2 tests) — full pipeline: `ConfluentKafkaContainer` + PostGIS container + real `KafkaEventHandler` + real `PostgisAdapter`. Sends a CloudEvent on `de.civitascore.data.sql.table.created` / `.deleted` and asserts (a) a `ConfigResultEvent` is published to the result topic with `SUCCESS` status and matching correlation ID, and (b) the table actually exists / was dropped in the database.

### Saga participation

Alongside the event-driven `PostgisAdapter`, the module provides `PostgisSagaHandler`, a `SagaCommandHandler` (ServiceLoader-registered) discovered by the application's `SagaComponentFactory` and registered in the saga orchestrator's `SagaHandlerRegistry`. It mirrors the FROST / APISIX / RedPanda handlers but executes DDL over JDBC. Because the JAX-RS-oriented `AbstractSagaCommandHandler` would pull a spurious HTTP dependency into this JDBC module, the handler implements `SagaCommandHandler` directly.

Forward/compensation pairs: `CREATE_TABLE`↔`DROP_TABLE`, `CREATE_SCHEMA`↔`DROP_SCHEMA`, `CREATE_ROLE`↔`DROP_ROLE`. The forward command carries the resource definition as a nested map (`tableConfig` / `schemaConfig` / `roleConfig`) including its `resourceType` discriminator — deserialized through the polymorphic `PostgisConfigValue` base, exactly as the config travels in CloudEvents. Each `CREATE_*` returns its identifiers as `compensationData`, which the orchestrator flattens into the compensating `DROP_*`'s payload. CREATE absorbs duplicate-object SQLStates and DROP absorbs missing-object SQLStates, so steps and compensations are retry-safe. The handler reuses `ConnectionProvider`, `PostgisDialect`, `TableDdlBuilder`, `GrantReconciler`, and the same `CredentialDecryptor`-based password handling as the adapter. It is **not** wired into the built-in dataset sagas (which remain FROST → APISIX → RedPanda); it is available to any saga referencing the `postgis` adapter.

### Connection-pool startup behaviour

`ConnectionProvider` configures HikariCP with `initializationFailTimeout = -1`. The pool is constructed lazily — adapter startup does **not** probe the database. If the DB is unreachable when an event arrives, the connection attempt fails inside `executeDdl`, the `SQLException` is classified by `PostgisDialect.isConnectivity` (`SQLState 08*`), and the event surfaces as a `RetryableAdapterException` — preserving the retry contract instead of crashing the adapter at startup. This mirrors the FROST adapter, which creates its JAX-RS client up front but defers real I/O to event processing.

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
