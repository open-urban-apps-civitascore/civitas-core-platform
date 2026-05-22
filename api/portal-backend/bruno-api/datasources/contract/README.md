# Data source connector contract tests

HTTP characterization tests pinning the current connector-configuration
contract for the `POST/GET/PUT/PATCH/DELETE /datasources` endpoints. This
is Phase 1 of issue #1391: the tests lock in the current behaviour so the
refactor in subsequent slices can be reviewed safely.

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

Each test is self-contained — it doesn't depend on resources created by
the `00-setup/` folder. Most scenarios pass `dataStructureVersionId: null`,
which the contract accepts and which keeps them independent of any shared
setup state. Release-lifecycle chains (`*-release-rejects-empty-*`) create
and link their own DSV within the chain.

## CONCERN tests

`*-empty-config-accepted-*.bru` pins current behaviour that is **not**
necessarily desired:

- For SQL, `POST` with `configuration: {}` returns 201 — none of `url`,
  `username`, `password`, or `query` are validated.
- For MQTT, `POST` with `configuration: {}` returns 201 — only `qos` is
  validated, `urls` and `topics` are not.

These tests are pinned to force a deliberate decision during the refactor.
If we decide to add field-level validation, the corresponding tests must
be deleted or inverted.

## Refactor scope

Slice 3 of the #1391 refactor will license edits to these tests when the
`type` discriminator is added to the `configuration` object. Until then,
the tests assert the **current** shape (no `type` field, divergent SQL
default schema, silently-dropped `url`/`username`/`query` fields, etc.).

The Java-side `DataSourceContractIntegrationTest$Persistence` covers the
raw JSONB shape stored in the database, which Bruno cannot reach. Keep
both halves in sync when updating fixtures.
