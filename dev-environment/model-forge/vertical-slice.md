# Vertical slice — portal-backend → Model Forge → Admin UI (shared database)

A one-shot **vertical slice**: create a schema through `portal-backend` and see the
resulting Model Forge **Element** in the Admin UI — with the Admin UI pointed at
portal-backend's *own* database.

> This is the counterpart to [local-demo.md](local-demo.md). There the Admin UI runs
> against a **separate** database (`model_forge_admin`) and therefore never shows what
> portal-backend created. Here the Admin UI reads portal-backend's `model_forge` schema
> directly, so you see exactly the artifacts the backend persisted.

## How it works

Model Forge is embedded in `portal-backend` and writes to the `model_forge` schema of the
`portal_backend` database. When you create a DataStructure **version with a `model`
(JSON Schema)**, `DataStructureVersionService` calls `ModelRegistryGateway.storeModel(...)`,
which persists a Model Forge Element. Point the Admin UI at that same database and the
Element shows up.

The Admin UI is started with:

* `SPRING_DATASOURCE_URL` → `…/portal_backend` (not its default `model_forge_admin`)
* `MODEL_FORGE_ADMIN_UI_SEED_ENABLED=false` → don't seed the SensorThings examples into
  portal-backend's registry
* `MODEL_FORGE_REGISTRY_MIGRATIONS_ENABLED=false` → the schema is already migrated by
  portal-backend; the Admin UI is a pure **reader** here
* `SERVER_PORT=8092` → avoid the Kafka-UI port (8090) used by the main stack

## Run it

```bash
# 1. Start the platform stack (portal-backend + embedded Model Forge, Keycloak, Postgres, …)
cd dev-environment
./start-portal-dev.sh --authz=allowall --config-adapter=auto --backend=auto --frontend=auto

# 2. Once healthy, run the vertical slice (creates an artifact, then launches the Admin UI)
bash model-forge/vertical-slice.sh
```

Then open **http://localhost:8092** and look for `VerticalSliceModel` under Elements.
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
