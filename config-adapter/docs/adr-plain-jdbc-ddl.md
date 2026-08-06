# ADR: Plain JDBC for event-driven DDL

## Context

The PostGIS adapter applies DDL to a PostgreSQL/PostGIS database in response to config events
arriving from Kafka. Each event carries a complete desired resource definition — a table, a schema,
or a role — and MUST be safe to re-deliver, since Kafka delivery is at-least-once. Some layer has to
render those definitions into statements and absorb re-delivery of an event whose resource exists.

## Options considered

| Option | Mechanism | Idempotency source |
|---|---|---|
| Plain JDBC | `SqlDialect` renders the statements, executed directly over JDBC | SQLState classification inside the adapter |
| Flyway | Each event becomes a versioned SQL migration | `flyway_schema_history` table |
| Liquibase | Each event becomes a changeSet with a deterministic id | Changelog history table |

Both migration tools offered a history table that makes re-application a no-op, an in-database audit
trail of schema changes, and dialect-aware rendering.

## Decision

Plain JDBC, with duplicate- and missing-object absorption keyed on SQLState inside the dialect.

| Reason | Detail |
|---|---|
| Model mismatch | Migration tools target versioned application-schema evolution fixed at build time, not ad hoc DDL arriving at runtime. Resumable migrations, baseline-and-diff and build-time validation have nothing to act on. |
| Ordering friction | Flyway expects monotonically increasing migration versions. Events carry no such order, so `outOfOrder=true` becomes mandatory — which disables the validation that justifies Flyway. |
| Idempotency is cheap without them | SQLState absorption lives behind the dialect's `isDuplicate` / `isMissing` classification, and keeps idempotency a property of the adapter rather than of an external table. |
| The audit argument does not hold here | Result events on the Kafka result topic carry the audit trail. A migration history table would be a second source of truth, with the drift risk that implies. |
| The dialect is the flavor seam | `SqlDialect` isolates every flavor-specific statement and error-code mapping, so a second SQL flavor costs no added dependency. |
| Operational footprint | A migration tool adds a runtime dependency, a managed table in the target database, and a class of failure modes — checksum mismatch, baseline drift — that plain JDBC does not have. |

## Consequences

- Idempotency is an adapter guarantee rather than a database one: a CREATE absorbs duplicate-object
  SQLStates and a DELETE absorbs missing-object SQLStates. Only the dialect knows PostgreSQL error
  codes.
- Statements are neither pre-checked nor rendered with `IF NOT EXISTS`. The database rejects the
  conflict and the adapter absorbs it.
- No in-database record of applied DDL exists. Result events are the sole audit trail.
- The decision is reversible at a single seam: a migration-tool integration would attach where
  `SqlDialect` renders statements.
