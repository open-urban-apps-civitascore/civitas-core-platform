# Vertical slice — portal-backend → Model Forge → Admin UI (shared database)

A one-shot **vertical slice**: create a schema through `portal-backend` and see the
resulting Model Forge **Element** in the Admin UI — with the Admin UI pointed at
portal-backend's *own* database.

> This is the counterpart to [local-demo.md](local-demo.md). There the Admin UI runs
> against a **separate** database (`model_forge_admin`) and therefore never shows what
> portal-backend created. Here the Admin UI reads portal-backend's `model_forge` schema
> directly, so you see exactly the artifacts the backend persisted.

> **NOT YET FUNCTIONAL — requires a portal-backend integration that does not exist yet.**
> `portal-backend` has no dependency on the Model Forge libraries, so creating a
> DataStructure version writes only its own entity and nothing to the registry. The script
> runs to completion but no Element appears, and the Admin UI shows an empty registry.
> This page describes the intended flow and stays as the harness for the integration step.

## How it works (intended)

Once portal-backend embeds Model Forge, it will write to the `model_forge` schema of the
`portal_backend` database: creating a DataStructure **version with a `model` (JSON Schema)**
will persist a Model Forge Element. Point the Admin UI at that same database and the Element
shows up.

The Admin UI is started with:

* `SPRING_DATASOURCE_URL` → `…/portal_backend` (not its default `model_forge_admin`)
* `MODEL_FORGE_ADMIN_UI_SEED_ENABLED=false` → don't seed the SensorThings examples into
  portal-backend's registry
* `SERVER_PORT=8093` → the main stack already uses 8090 (Kafka UI), 8091
  (authz-repository) and 8092 (the `model-forge-admin-ui` container)

Migrations are left **enabled** (the default): nothing else creates the `model_forge`
schema today, so the Admin UI creates it. Disabling them against a database where the
schema is absent makes startup fail with
`relation "model_forge.artifact" does not exist` — and it fails *after* logging
`Started AdminUiApplication`, so check the exit status rather than the last log line.

## Run it

```bash
# 1. Start the platform stack (portal-backend, Admin UI, Keycloak, Postgres, …)
cd dev-environment
./start-portal-dev.sh --authz=allowall --config-adapter=auto --backend=auto --frontend=auto

# 2. Once healthy, run the vertical slice (creates an artifact, then launches the Admin UI)
bash model-forge/vertical-slice.sh
```

Then open **http://localhost:8093** and look for `VerticalSliceModel` under Elements.
Ctrl+C stops the Admin UI; the created artifact stays in the database.

## Requirements

* Platform stack running (Step 1); `curl` and `node` on PATH (node ships with the repo's
  frontend tooling; used only to parse JSON responses — no `jq` needed).
* Java 25 + Maven (the Admin UI runs via `mvn spring-boot:run`).

## Notes / knobs

* All endpoints and credentials default to the Bruno `local` environment
  (`api/portal-backend/bruno-api/environments/local.bru`) and are overridable via env vars
  at the top of `vertical-slice.sh`.
* Want more artifacts (DataSource/DataSink/Pipeline payloads too)? Run the Bruno setup folder
  instead of / in addition to the script's curl step:
  `cd api/portal-backend/bruno-api && npx @usebruno/cli run 00-setup --env local-direct`
  (create-only, no teardown).
