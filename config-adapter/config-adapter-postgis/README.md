# PostGIS Config Adapter

Adapter for PostgreSQL/PostGIS **tables**, **schemas** and **roles** (including schema-level grants). It
applies DDL over JDBC in response to CloudEvents on Kafka, and registers a saga command handler so the same
DDL participates as a compensable step in the dataset sagas. It does not install the PostGIS extension, does
not alter existing tables, and never touches table data. PostGIS specifics (geometry columns, SRID, GIST
indexes) and all flavour-specific SQL are confined to the `SqlDialect` seam. The choice of plain JDBC over a
schema-migration tool is recorded in [../docs/adr-plain-jdbc-ddl.md](../docs/adr-plain-jdbc-ddl.md).

## Operations

The payload's `resourceType` selects the resource family and the event's operation selects the action; a
payload that is none of the three is rejected.

| Resource | `resourceType` | CREATE | UPDATE | DELETE |
|---|---|---|---|---|
| Table | `sql-table` | yes — schema, table, indexes | no — `UNSUPPORTED_OPERATION` | yes |
| Schema | `sql-schema` | yes — optional `AUTHORIZATION` owner | yes — owner change | yes — `RESTRICT`, or `CASCADE` when `cascade` is set |
| Role | `sql-role` | yes — attributes, password, grants | yes — attributes, password, grant reconciliation | yes |

`postgis.topics` selects from `de.civitascore.data.sql.table.{created,deleted}`,
`de.civitascore.data.sql.schema.{created,updated,deleted}` and
`de.civitascore.data.sql.role.{created,updated,deleted}`. A table UPDATE has no in-place equivalent, so
`de.civitascore.data.sql.table.updated` MUST NOT be subscribed. Result events go to the `resultTopic` from
the incoming metadata, with CloudEvent type `de.civitascore.data.sql.processing.result`.

| Forward | Payload field | Compensation | Compensation payload |
|---|---|---|---|
| `CREATE_TABLE` | `tableConfig` | `DROP_TABLE` | `{schema?, table}` |
| `CREATE_SCHEMA` | `schemaConfig` | `DROP_SCHEMA` | `{schema, cascade?}` |
| `CREATE_ROLE` | `roleConfig` | `DROP_ROLE` | `{role}` |
| `PROVISION_SINK` | `datasinks`, `datasetId` | `DEPROVISION_SINK` | `datasinks`, `datasetId` |

Each nested config map carries its `resourceType` discriminator, exactly as the config travels inside
CloudEvents. Every `CREATE_*` returns its identifiers as compensation data, which the orchestrator flattens
into the payload of the compensating `DROP_*`; a `DROP_*` also serves as a forward step. A failed saga
command returns a command failure carrying the redacted database message, and the orchestrator drives
compensation.

## Behaviour

- **One transaction per event or command**, rolled back before the exception propagates. Each statement is
  wrapped in a savepoint, because PostgreSQL aborts the whole transaction on any statement error — the
  savepoint lets an absorbed duplicate or missing object be skipped while the remaining statements of a
  multi-statement plan (schema → table → index, or role → grants) commit.
- **Idempotency without pre-checks.** No existence check and no `IF NOT EXISTS`: the database fails naturally
  and the adapter absorbs the conflict, so idempotency is a property of the adapter, not of external state.
- **Grant reconciliation.** On a role UPDATE the payload's grants are the desired state: the adapter reads the
  role's current schema privileges and grant-option state from the database, `GRANT`s those newly present and
  `REVOKE`s those the payload omits. A held privilege is re-granted `WITH GRANT OPTION` when the payload asks
  for it, and downgraded through `REVOKE GRANT OPTION FOR …` when it does not.
- **Role passwords.** A `password` may be an encrypted `ENC(...)` value, decrypted with the
  `CIVITAS_MASTER_KEY` master key under the credential context `portal-backend:sql-role`; plaintext passes
  through unchanged, and an encrypted password without the master key fails the event fatally.
  `PASSWORD '…'` literals are redacted from logs and error messages, JDBC URL credentials are masked, and the
  stretched key is zeroed on shutdown.
- **Startup does not depend on the database.** The pool starts eagerly but skips the fail-fast initial
  connection, so an unreachable database does not prevent startup; the failure surfaces as a retryable error
  when an event arrives. `CREATE EXTENSION postgis` is an operator-provisioned prerequisite.

| SQLState | Meaning | Outcome |
|---|---|---|
| `42P07` | duplicate table or index | success on a CREATE |
| `42P06` | duplicate schema | success on a CREATE |
| `42710` | duplicate role | success on a CREATE |
| `42P01` | undefined table | success on a DROP |
| `3F000` | invalid schema name | success on a DROP |
| `42704` | undefined object | success on a DROP |
| `08xxx` | connection failure | retryable |
| other | data, integrity, syntax and privilege errors | fatal, transaction rolled back |

A table payload carries `schema`, `name`, `columns`, `geometryColumns`, `primaryKey` and `indexes`; a CREATE
renders `CREATE SCHEMA`, then `CREATE TABLE` with the columns, geometry columns as `GEOMETRY(<type>, <srid>)`
and the `PRIMARY KEY`, then one `CREATE INDEX … USING <method>` per index. A role attribute clause carries
`LOGIN`/`NOLOGIN` and, where the payload sets them, `SUPERUSER`, `CREATEDB`, `CREATEROLE` and `INHERIT`, with
a password appended last; a database user is a role with `LOGIN`, so one payload models both. A role DELETE
renders `DROP OWNED BY` before `DROP ROLE`, since PostgreSQL refuses to drop a role holding privileges. A
schema UPDATE renders `ALTER SCHEMA … OWNER TO …`, and no statement at all when the payload gives no owner.

| Field | Accepted values |
|---|---|
| Column `type` | `SMALLINT`, `INTEGER`, `BIGINT`, `NUMERIC` (optional `precision`/`scale`), `REAL`, `DOUBLE_PRECISION`, `BOOLEAN`, `VARCHAR` (optional `length`), `TEXT`, `UUID`, `DATE`, `TIME`, `TIMESTAMP`, `TIMESTAMPTZ`, `JSONB`, `BYTEA` |
| `geometryType` | `POINT`, `LINESTRING`, `POLYGON`, `MULTIPOINT`, `MULTILINESTRING`, `MULTIPOLYGON`, `GEOMETRYCOLLECTION`, `GEOMETRY`. An optional `dimension` of `3` appends `Z` and `4` appends `ZM` to the rendered type |
| Index `method` | `BTREE` (default), `GIST` (required for geometry columns), `GIN` |
| Grant `privileges` | `USAGE`, `CREATE`, `ALL` (expands to `USAGE` + `CREATE`) |

## Dataset sink provisioning

`PROVISION_SINK` creates the PostGIS objects a GeoServer datastore publishes from, which is why the dataset
CREATE saga runs it before GeoServer registers that datastore. For every `POSTGIS` entry in the trigger's
`datasinks`, one transaction creates the schema, the table and an optional read role with its grants, from
that entry's `configuration` (`tableName`, `columns`, `geometryColumns`, `primaryKey`, `readRole`) and its
`dataStructure` JSON Schema.

- **Schema** — always derived from the trigger's `datasetId`, by the same rule that produces the GeoServer
  workspace name, so the table lands in the schema GeoServer reads from. `configuration` carries no schema and
  no owner. A trigger without a `datasetId` fails the step rather than falling back to `public`.
- **Columns** — explicit `configuration.columns` win; otherwise columns are derived from `dataStructure`,
  excluding explicitly configured `geometryColumns`. At least one column or geometry column MUST result, and a
  schema with no usable properties fails the step.
- **Primary key** — explicit `configuration.primaryKey` wins; otherwise the `x-core-primaryKey` markers in
  `dataStructure` supply it, through the same resolver the NiFi adapter uses, so the table primary key and the
  pipeline's UPSERT keys cannot diverge. Primary-key columns are `NOT NULL` even when `required` omits them,
  and a primary key naming a column absent from the table fails the step rather than emitting broken DDL.
- **Read role** — optional. It receives the listed schema privileges, defaulting to `USAGE`, plus `SELECT` on
  the sink table. Its password follows the same `ENC(...)` handling as any role password.
- **Teardown** — `DEPROVISION_SINK` drops the role, then the table, then the per-dataset schema with
  `RESTRICT`, skipping `public`. A schema left non-empty fails the step; nothing is force-dropped. Forward
  delete and compensation re-derive their targets from `datasinks` and `datasetId`, so both paths resolve
  identical identifiers. A delete trigger may omit `columns` and `dataStructure`.

Column derivation reads the root `properties`, or the property-carrying definition under
`$defs`/`definitions`; `required` properties become `NOT NULL`.

| JSON Schema type | Column |
|---|---|
| `integer` | `BIGINT` |
| `number` | `DOUBLE_PRECISION` |
| `boolean` | `BOOLEAN` |
| `string` with format `date-time` / `date` / `time` / `uuid` | `TIMESTAMPTZ` / `DATE` / `TIME` / `UUID` |
| `object`, `array` | `JSONB` |
| `$ref` to a GeoJSON schema (`https://geojson.org/schema/<Type>.json`) | geometry column; SRID from the property's `crs` in EPSG form, default `4326` |
| any other `$ref`, including a local `#/$defs/<Type>` | `JSONB` |
| `string`, unknown | `TEXT` |

Geometry is recognized by the GeoJSON-hosted `$ref` alone, so a local `#/$defs/Point` is a nested object, not
a geometry column. A definition's columns are gathered by following its `allOf` branches and node-level local
`$ref` parents, so a subclass carrying its fields under `allOf` yields the full column set: inherited columns
come first, a subclass wins the column type on a name collision while the column keeps its first-seen
position, and each definition's `required` entries are unioned. A node-level parent `$ref` resolving to no
definition is rejected, since it would drop inherited columns into a partial table; a property-level `$ref`
stays `JSONB` and is never followed as a parent.

## Configuration

Keys carry the `postgis.` prefix. The values that ship, env-var names and secret handling live in
[../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Role |
|---|---|
| `postgis.topics` | Topics subscribed on the config-event path |
| `postgis.jdbc.url` | Target database |
| `postgis.jdbc.user` | Connecting role; needs the privileges for the DDL it issues |
| `postgis.jdbc.password` | Passed through verbatim; `ENC(…)` is not supported here |
| `postgis.jdbc.maxPoolSize` | Connection pool size |
| `postgis.jdbc.connectionTimeoutMs` | Pool connection timeout |
| `CIVITAS_MASTER_KEY` (env var only) | Decrypts `ENC(…)` role passwords carried in event payloads |

An unresolved `postgis.jdbc.url` or `postgis.jdbc.user` fails initialization with a message naming the key, but both
ship with a value — so an unset `POSTGIS_JDBC_URL` connects to the development database rather than failing. A
non-numeric `postgis.jdbc.maxPoolSize` or `postgis.jdbc.connectionTimeoutMs` fails initialization with a
number-format error naming only the offending value, not the key. A missing `CIVITAS_MASTER_KEY` is logged at
startup and fails any event carrying an encrypted password.

## Error codes

| Code | Meaning | Retryable |
|---|---|---|
| `INVALID_PAYLOAD(1001)` | Payload is neither a table, schema nor role config | no |
| `UNSUPPORTED_OPERATION(1004)` | Table UPDATE | no |
| `POSTGIS_ERROR(3501)` | Encrypted role password without a master key, or decryption failure | no |
| `POSTGIS_DDL_ERROR(3502)` | DDL rejected by the database; transaction rolled back | no |
| `POSTGIS_CONNECTION_ERROR(3503)` | SQLState class `08` — database unreachable | yes |

## Testing

```bash
mvn test -pl config-adapter-postgis                           # unit tests, no Docker
mvn test -pl config-adapter-postgis -Dtest=PostgisDialectTest # a single class or method
mvn -pl config-adapter-postgis -am verify                     # adds integration and E2E, needs Docker
```

Integration tests run against a real PostGIS container, whose image version lives in the shared
`TestContainerImages` constants. They assert that the generated DDL is accepted by PostgreSQL and the objects
physically exist, that saga forward steps and their compensations converge, and that a CloudEvent travels
through Kafka to a committed database change and a result event.
