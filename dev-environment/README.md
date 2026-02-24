# CIVITAS CORE – Development Environment

Docker Compose setup for running the **CIVITAS CORE Platform** locally.

---

## Structure

```
dev-environment/
├── backend/    # Full backend stack — all services in Docker
├── authz/      # AuthZ test data and integration tests
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

### Option B: Portal Dev Mode (recommended for development)

Starts all infrastructure in Docker, prompts for backend/frontend startup
and authorization mode.

```bash
./start-portal-dev.sh                # Interactive prompts
./start-portal-dev.sh --authz=full   # Full AuthZ (enforce permissions)
./start-portal-dev.sh --authz=allowall  # Allow-all (any logged-in user can do anything)
```

### Authorization Modes

| Mode | Flag | Behavior |
|------|------|----------|
| **Full** | `--authz=full` | OPA enforces per-endpoint permissions. Users need role assignments. |
| **Allow-all** | `--authz=allowall` | Any logged-in user can access all endpoints. OPA still runs (logs decisions) but uses null-permission data. APISIX injects wildcard scope header. |

Both modes require a valid JWT (Keycloak login). Allow-all is useful when
working on features unrelated to authorization. Integration tests
(`authz/integration-test.sh`) require full mode.

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

## Stopping

```bash
./stop-portal-dev.sh            # Stop all services (prompts to remove volumes)
```

Or manually per service:

```bash
cd apisix    && docker compose down && docker compose -f docker-compose.authz.yml down
cd kafka     && docker compose down
cd keycloak  && docker compose down
cd postgres  && docker compose down
cd frost     && docker compose down
```

## Common Commands

```bash
docker compose up --build       # Rebuild and start
docker compose down             # Stop services
docker compose down -v          # Stop and remove volumes
docker compose logs -f          # Follow logs
docker compose ps               # List running services
```

---