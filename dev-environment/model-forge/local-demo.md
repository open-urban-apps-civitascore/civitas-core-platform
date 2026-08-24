# Local Demo Environment — Portal Backend + Model Forge + Admin UI + Frontend

End-to-end local setup covering the full platform stack (Portal Backend,
Portal Frontend, PostgreSQL, Keycloak, APISIX, Kafka, FROST, ...) plus the
Model Forge Admin UI for inspecting the model registry.

## Architecture in one paragraph

Model Forge is **not a standalone service** — it is a set of Java libraries meant to be
embedded in a host application. **`portal-backend` does not consume them yet**: that
wiring is a later step, so starting the platform stack does *not* give you a running
Model Forge inside the backend, and the backend writes nothing to the registry.

The only host that consumes Model Forge today is the **Admin UI**
(`model-forge-admin-ui`), a Wicket/Spring Boot console over the model registry. It is
started by `start-portal-dev.sh` as the `model-forge-admin-ui` service (port 8092;
disable with `--no-admin-ui`), and by **default it points at portal-backend's own
database**, where it creates and owns the `model_forge` schema itself via its own Flyway
migration. It is read-WRITE: imports/edits/deletes here hit that registry directly,
bypassing portal-backend's own API and its service-layer rules.

> **The Admin UI has no authentication.** Anything that can reach its port has full
> read-write access to the registry. Keep it to a local machine.

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
GeoServer, NiFi, then `config-adapter`, `portal-backend`, the Model Forge Admin UI
and `portal-frontend`. See [../README.md](../README.md) for all
flags, interactive prompts, and the Keycloak client secret needed by the
frontend.

Wait for it to report all services healthy before continuing.

## Step 2 — The Model Forge Admin UI

Step 1 already starts it as the `model-forge-admin-ui` service on
**http://localhost:8092** (pass `--no-admin-ui` to skip it). Nothing further is needed.

The registry it shows will be **empty** on a fresh stack: it points at portal-backend's
database, but portal-backend does not write to Model Forge yet, and the Admin UI creates
the `model_forge` schema itself on first start. Anything you see there is what *you* put
there through these pages. To browse the bundled OGC SensorThings examples instead, use
standalone mode below.

To run it by hand rather than as a container — pick a free port, since its default 8092 is
already taken by the container from Step 1 (and 8090 is Kafka UI):

```bash
cd ../../model-forge/model-forge-admin-ui
SERVER_PORT=8093 mvn spring-boot:run
```

> The Admin UI is read-WRITE and unauthenticated — imports/edits/deletes here change the
> `model_forge` registry in portal-backend's database directly.

### Standalone mode — own seeded database (no portal-backend)

To explore the bundled OGC SensorThings examples without running portal-backend,
point the Admin UI at its own database (started from this folder) and enable the
seed. That dedicated PostgreSQL uses **host port 5439** (5432 is the main stack's
PostgreSQL and 5433 is Keycloak's, so both would collide):

```bash
cd dev-environment/model-forge
docker compose up -d

cd ../../model-forge/model-forge-admin-ui
SERVER_PORT=8093 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5439/model_forge_admin?sslmode=disable \
SPRING_DATASOURCE_USERNAME=model_forge SPRING_DATASOURCE_PASSWORD=model_forge \
MODEL_FORGE_ADMIN_UI_SEED_ENABLED=true \
mvn spring-boot:run
```

Open **http://localhost:8093**. On first start it seeds the bundled OGC
SensorThings example schemas, so there is something to explore immediately.

### Why the port overrides

The Admin UI's [application.yml](../../model-forge/model-forge-admin-ui/src/main/resources/application.yml)
defaults to port `8092` and datasource `localhost:5432/portal_backend` (user
`admin`/`admin`) — exactly what the container from Step 1 uses. Running a *second*
instance by hand alongside that container therefore needs a port override:

| Port | Main stack | Admin UI default | Conflict |
|------|------------|-------------------|----------|
| `5432` | PostgreSQL (portal-backend's DB) | the Admin UI's default datasource | No — that is the intended target |
| `5433` | PostgreSQL (Keycloak's DB) | — | Would collide with a standalone DB; hence `5439` |
| `8090` | Kafka UI | — | — |
| `8092` | Model Forge Admin UI container (Step 1) | Admin UI web app | Yes → override with `SERVER_PORT=8093` |

The shipped defaults describe **local-demo mode**: the `portal_backend` database with
`admin`/`admin`. They do *not* match this module's own
[docker-compose.yml](../../model-forge/model-forge-admin-ui/docker-compose.yml), which
creates database `model_forge_admin` with user `model_forge` — that compose (a duplicate of
this folder's, same container name and host port `5439`) belongs to **standalone mode**, so
starting it without also passing the `SPRING_DATASOURCE_*` overrides from the section above
leaves the Admin UI pointed at `portal_backend` and the new database unused.

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
| Model Forge Admin UI | http://localhost:8093 | with the overrides above |

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
`docker-compose.yml` (`5439` by default).

**Admin UI won't bind its port** — something is already on `8093`, or you forgot
`SERVER_PORT` and it collided with the Step 1 container on its default `8092`. The main
stack also already publishes `8089`, `8090` and `8091`, so pick a port outside that set.

**Admin UI is empty** — expected on a fresh stack. It points at portal-backend's
database, but portal-backend does not write to Model Forge yet, so the registry only
contains what you create through these pages. Use **standalone mode** (above) if you want
the bundled SensorThings examples to browse.

**Admin UI won't start, log says `relation "model_forge.artifact" does not exist`** — it was
started with `MODEL_FORGE_REGISTRY_MIGRATIONS_ENABLED=false` against a database where
nothing has created the schema. Leave migrations enabled (the default) so the Admin UI
creates it. Note the process logs `Started AdminUiApplication` *before* failing, so check
the exit status, not just the last log line.
