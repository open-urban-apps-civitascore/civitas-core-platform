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
├── geoserver/  # GeoServer OGC Services (WFS/WMS)
└── modelatlas/ # Model Atlas
```

---

## Prerequisites

* Docker + Docker Compose v2
* Java 21+ JDK (any distribution: Temurin, OpenJDK, Oracle, GraalVM)
* Maven 3.9+
* jq (for dev-mode scripts)

Supported platforms: **Linux**, **macOS** (including Apple Silicon / ARM), and **Windows** (WSL / Git Bash).

---

## Quick Start

### Option A: Full Docker (CI, demos)

```bash
cd dev-environment
./start-portal-dev.sh
```

This script:
1. Starts all infrastructure services (Kafka, PostgreSQL, Keycloak, APISIX, FROST)
2. Asks how you want to start each backend service:
   - **auto**: builds and starts the service in a new terminal window
   - **ide**: prints IDE setup instructions for debugging in Eclipse/IntelliJ
3. Optionally starts the portal frontend

On Linux, new terminal windows are opened via `gnome-terminal` or `xterm`.
On macOS, new terminal windows are opened via `Terminal.app`.

### Command-line Options

All interactive prompts can be bypassed with command-line flags, which is useful for scripting or quick restarts:

```
Usage: start-portal-dev.sh [OPTIONS]

Options:
  --config-adapter=auto|ide    Config Adapter startup (default: prompt)
  --backend=auto|ide           Portal Backend startup (default: prompt)
  --frontend=auto|manual|skip  Portal Frontend startup (default: prompt)
  --keycloak-secret=SECRET     Keycloak client secret for portal-frontend
  -h, --help                   Show this help message
```

**Examples:**

```bash
# Fully non-interactive: start everything automatically
./start-portal-dev.sh --config-adapter=auto --backend=auto --frontend=auto

# Run backend in IDE, skip frontend
./start-portal-dev.sh --config-adapter=ide --backend=ide --frontend=skip

# Provide the Keycloak client secret directly
./start-portal-dev.sh --backend=auto --keycloak-secret=<secret>
```

### Keycloak Client Secret

The portal frontend requires a Keycloak client secret. If not yet configured in
`portal-frontend/.env.local`, the script will prompt for it (or accept it via
`--keycloak-secret`). The secret can be found in the Keycloak Admin UI:

> Realm: `civitas-core` → Clients → `portal-frontend` → Credentials

### Stopping All Services

```bash
cd dev-environment
./stop-portal-dev.sh
```

---

## Full Stack Mode (All in Docker)

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

On Linux, new terminal windows are opened via `gnome-terminal` or `xterm`.
On macOS, new terminal windows are opened via `Terminal.app`.

### Command-line Options

All interactive prompts can be bypassed with command-line flags, which is useful for scripting or quick restarts:

```
Usage: start-portal-dev.sh [OPTIONS]

Options:
  --authz=full|allowall        AuthZ mode (default: prompt, default answer: allowall)
  --config-adapter=auto|ide    Config Adapter startup (default: prompt)
  --backend=auto|ide           Portal Backend startup (default: prompt)
  --frontend=auto|manual|skip  Portal Frontend startup (default: prompt)
  --keycloak-secret=SECRET     Keycloak client secret for portal-frontend
  -h, --help                   Show this help message
```

**Examples:**

```bash
# Fully non-interactive: start everything automatically
./start-portal-dev.sh --authz=allowall --config-adapter=auto --backend=auto --frontend=auto

# Run backend in IDE, skip frontend
./start-portal-dev.sh --config-adapter=ide --backend=ide --frontend=skip

# Provide the Keycloak client secret directly
./start-portal-dev.sh --backend=auto --keycloak-secret=<secret>
```

### Authorization Modes

| Mode | Flag | Behavior |
|------|------|----------|
| **Full** | `--authz=full` | OPA enforces per-endpoint permissions. Users need role assignments. |
| **Allow-all** | `--authz=allowall` | Any logged-in user can access all endpoints. OPA still runs (logs decisions) but uses null-permission data. APISIX injects wildcard scope header. |

Both modes require a valid JWT (Keycloak login). Allow-all is useful when
working on features unrelated to authorization. Integration tests
(`authz/integration-test.sh`) require full mode.

### Keycloak Client Secret

The portal frontend requires a Keycloak client secret. If not yet configured in
`portal-frontend/.env.local`, the script will prompt for it (or accept it via
`--keycloak-secret`). The secret can be found in the Keycloak Admin UI:

> Realm: `civitas-core` → Clients → `portal-frontend` → Credentials

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
| Portal Backend | http://localhost:8089 | Swagger: /v1/swagger-ui.html |
| Config Adapter | http://localhost:8088 | |
| APISIX Gateway | http://localhost:9080 | Routes to backend via OPA authz |
| OPA | http://localhost:8181 | Policy decision point |
| AuthZ Repository | http://localhost:8091 | User authorization context |
| Kafka UI | http://localhost:8090 | |
| FROST Server | http://localhost:8085/FROST-Server/v1.1 | |
| GeoServer Admin | http://localhost:8082/geoserver/web | admin / see geoserver/.env |
| GeoServer WFS | http://localhost:9080/geoserver/{workspace}/wfs | via APISIX |

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
cd geoserver && docker compose down
```

## Troubleshooting

**Something isn't working after pulling new changes?**

```bash
./stop-portal-dev.sh            # say "yes" to remove volumes
./start-portal-dev.sh
```

This wipes stale Keycloak state, database data, and cached configs. Fixes most issues.

**Keycloak login redirects fail or tokens are rejected?**

Re-copy the frontend env template — the Keycloak issuer URL may have changed:

```bash
cp portal-frontend/.env.local.template portal-frontend/.env.local
```

**Running from a VM (not localhost)?**

Override the Keycloak hostname before starting:

```bash
export KC_HOSTNAME=http://<your-vm-ip>:8080
./start-portal-dev.sh
```

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
