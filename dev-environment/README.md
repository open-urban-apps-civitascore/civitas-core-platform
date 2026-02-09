# CIVITAS CORE – Development Environment

Docker Compose setup for running the **CIVITAS CORE Platform** locally.

---

## Structure

```
dev-environment/
├── backend/    # Full backend stack — all services in Docker
├── authz/      # Dev-mode startup — backend runs locally with hot-reload
├── kafka/      # Kafka only
├── postgres/   # PostgreSQL only
├── keycloak/   # Keycloak only
├── apisix/     # API Gateway + Authorization (OPA, AuthZ Repository)
├── frost/      # FROST IoT Server
└── modelatlas/ # Model Atlas
```

---

## Prerequisites

* Docker + Docker Compose v2
* Java 21 JDK
* Maven 3.9+
* jq (for dev-mode scripts)
* `/etc/hosts` entry: `127.0.0.1 civitas-keycloak`

---

## Quick Start

### Option A: Full Docker (CI, demos)

All services run in Docker containers. No hot-reload for backend changes.

```bash
cd backend
./start.sh
```

Starts: Kafka, PostgreSQL, Keycloak, APISIX, OPA, AuthZ Repository,
Portal Backend, and Config Adapter.

### Option B: Dev Mode (recommended for development)

Backend runs locally with Spring Boot devtools hot-reload.
Infrastructure and authorization services run in Docker.

```bash
cd authz
./start-dev.sh              # Full stack from scratch
./start-dev.sh --skip-build # Skip Maven builds (use existing JARs)
```

Features: idempotent (safe to re-run), health-checked startup with timeouts,
`--skip-build` flag, automatic test user and data seeding.

See [authz/README.md](authz/README.md) for details.

---

## Run Individual Services

```bash
cd kafka     && docker compose up -d
cd postgres  && docker compose up -d
cd keycloak  && docker compose up -d
cd apisix    && docker compose up -d
```

---

## Key URLs

| Service | URL | Notes |
|---------|-----|-------|
| Keycloak | http://localhost:8080 | admin / admin |
| Portal Backend | http://localhost:8089 | Swagger: /v2/swagger-ui.html |
| Config Adapter | http://localhost:8088 | |
| APISIX Gateway | http://localhost:9080 | Routes to backend via OPA authz |
| OPA | http://localhost:8181 | Policy decision point |
| AuthZ Repository | http://localhost:8091 | User authorization context |
| Kafka UI | http://localhost:8090 | |

---

## Common Commands

```bash
docker compose up --build       # Rebuild and start
docker compose down             # Stop services
docker compose down -v          # Stop and remove volumes
docker compose logs -f          # Follow logs
docker compose ps               # List running services
```

---