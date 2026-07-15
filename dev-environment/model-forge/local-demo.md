# Local Demo Environment — Portal Backend + Model Forge + Admin UI + Frontend

End-to-end local setup covering the full platform stack (Portal Backend,
Portal Frontend, PostgreSQL, Keycloak, APISIX, Kafka, FROST, ...) plus the
Model Forge Admin UI for inspecting the model registry.

## Architecture in one paragraph

Model Forge is **not a standalone service** — it is a Java library embedded in
`portal-backend` and shares its PostgreSQL database. Starting the platform
stack (Step 1) therefore already gives you a running Model Forge; there is
nothing separate to start for it.

The **Admin UI** (`model-forge-admin-ui`) is a separate Wicket/Spring Boot
console over the model registry. It is not part of `start-portal-dev.sh`, but by
**default it points at portal-backend's own database** (the `model_forge`
schema) — so it shows and manages exactly what portal-backend created. It is
read-WRITE: imports/edits/deletes here hit the real registry. To create an
artifact via portal-backend and jump straight to it, use
[vertical-slice.sh](vertical-slice.sh) / [vertical-slice.md](vertical-slice.md).

To run the Admin UI **standalone** — without portal-backend, against its own
seeded database with the bundled OGC SensorThings examples — use the overrides
in Step 2 below.

## Prerequisites

Same as the main dev environment (see [../README.md](../README.md)):

* Docker + Docker Compose v2
* Java 25+ JDK and Maven 3.9+ (only if running the Admin UI, or the backend in
  `cmd`/`ide` mode instead of `auto`)

## Step 1 — Start the platform stack

```bash
cd dev-environment
./start-portal-dev.sh --authz=allowall --config-adapter=auto --backend=auto --frontend=auto
```

This brings up Kafka, PostgreSQL, Keycloak, APISIX/OPA/AuthZ, FROST,
GeoServer, NiFi, then `config-adapter`, `portal-backend` (with embedded Model
Forge) and `portal-frontend`. See [../README.md](../README.md) for all
flags, interactive prompts, and the Keycloak client secret needed by the
frontend.

Wait for it to report all services healthy before continuing.

## Step 2 — Start the Model Forge Admin UI (optional)

By **default** the Admin UI points at portal-backend's database (from Step 1), so
it already shows what the backend created. It just needs a free port (8090 is
taken by Kafka UI in the main stack):

```bash
cd ../../model-forge/model-forge-admin-ui
SERVER_PORT=8091 mvn spring-boot:run
```

Open **http://localhost:8091**. To also create a sample artifact first and jump
straight to it, use [vertical-slice.sh](vertical-slice.sh) instead.

> The Admin UI is read-WRITE — imports/edits/deletes here change portal-backend's
> real `model_forge` registry.

### Standalone mode — own seeded database (no portal-backend)

To explore the bundled OGC SensorThings examples without running portal-backend,
point the Admin UI at its own database (started from this folder) and enable the
seed. That dedicated PostgreSQL uses **host port 5433** (5432 would collide with
the main stack's PostgreSQL):

```bash
cd dev-environment/model-forge
docker compose up -d

cd ../../model-forge/model-forge-admin-ui
SERVER_PORT=8091 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/model_forge_admin?sslmode=disable \
SPRING_DATASOURCE_USERNAME=model_forge SPRING_DATASOURCE_PASSWORD=model_forge \
MODEL_FORGE_ADMIN_UI_SEED_ENABLED=true \
mvn spring-boot:run
```

Open **http://localhost:8091**. On first start it seeds the bundled OGC
SensorThings example schemas, so there is something to explore immediately.

### Why the port overrides

The Admin UI's [application.yml](../../model-forge/model-forge-admin-ui/src/main/resources/application.yml)
defaults to port `8090` and database `localhost:5432` — fine when run in
isolation, but both collide with the main stack when run together for this
demo:

| Port | Main stack | Admin UI default | Conflict |
|------|------------|-------------------|----------|
| `5432` | PostgreSQL (portal-backend's DB) | Admin UI's own DB | Yes → compose here uses `5433` instead |
| `8090` | Kafka UI | Admin UI web app | Yes → override with `SERVER_PORT=8091` |

If you only ever run the Admin UI on its own (Step 1 stopped), the defaults
(`8090` / `5432`, no env overrides, `docker-compose.yml` in
[../../model-forge/model-forge-admin-ui/](../../model-forge/model-forge-admin-ui/))
work fine too — the port shift above is only needed for running both stacks
side by side, which is what this guide is about.

## Key URLs (combined)

| Service | URL | Notes |
|---|---|---|
| Keycloak | http://localhost:8080 | admin / admin |
| Portal Backend | http://localhost:8089 | Swagger: `/v1/swagger-ui.html` |
| Portal Frontend | via `apps/` compose | Keycloak login |
| APISIX Gateway | http://localhost:9080 | Routes through OPA authz |
| Kafka UI | http://localhost:8090 | |
| FROST Server | http://localhost:8085/FROST-Server/v1.1 | |
| GeoServer Admin | http://localhost:8082/geoserver/web | admin / see `../geoserver/.env` |
| Model Forge Admin UI | http://localhost:8091 | with the overrides above |

See [../README.md](../README.md) for the full list (OPA, AuthZ Repository,
Apache NiFi, ...).

## Stopping

```bash
# Model Forge Admin UI
# Ctrl+C the `mvn spring-boot:run` process, then:
cd dev-environment/model-forge && docker compose down

# Main stack
cd dev-environment && ./stop-portal-dev.sh
```

## Troubleshooting

**Admin UI fails to connect to Postgres** — confirm `docker compose ps` in
this folder shows `model-forge-admin-postgres` as healthy, and that
`SPRING_DATASOURCE_URL` in the run command matches the port in this folder's
`docker-compose.yml` (`5433` by default).

**Admin UI won't bind its port** — something is already on `8091`, or you
forgot `SERVER_PORT=8091` and it collided with Kafka UI on `8090`.

**Admin UI shows portal-backend's data** — expected in the default mode: the
Admin UI points at portal-backend's database and is a live console over the same
`model_forge` registry. Use **standalone mode** (above) if you want an isolated,
seeded copy instead.
