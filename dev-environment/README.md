# CIVITAS CORE – Development Environment

Docker Compose setup for running the **CIVITAS CORE Platform** locally.
You can run the **full backend stack** or use the **portal development mode** for IDE debugging.

---

## Structure

```
dev-environment/
├── backend/           # Full backend stack (all in Docker)
├── kafka/             # Kafka + Zookeeper + Kafka UI
├── postgres/          # PostgreSQL (Portal + Keycloak databases)
├── keycloak/          # Keycloak Identity Provider
├── apisix/            # API Gateway + etcd
├── frost/             # FROST SensorThings API Server
├── modelatlas/        # Model Atlas Service
├── start-portal-dev.sh   # Portal development startup script
└── stop-portal-dev.sh    # Portal development stop script
```

---

## Prerequisites

* Docker + Docker Compose v2
* Java 21+
* Maven 3.6+
* Node.js 18+ and pnpm (for frontend)

---

## Portal Development Mode (Recommended for Debugging)

Use this mode when you want to run and debug the backend services in your IDE.

```bash
cd dev-environment
./start-portal-dev.sh
```

This script:
1. Starts all infrastructure services (Kafka, PostgreSQL, Keycloak, APISIX, FROST)
2. Asks how you want to start the backend services:
   - **Command line**: Starts config-adapter and portal-backend via Maven
   - **Manual/IDE**: Shows instructions for starting in Eclipse/IntelliJ for debugging

After infrastructure is running, start the frontend:
```bash
cd portal-frontend
pnpm install   # first time only
pnpm dev
```

To stop all services:
```bash
cd dev-environment
./stop-portal-dev.sh
```

---

## Full Stack Mode (All in Docker)

Start everything in Docker containers:

```bash
cd backend
./start.sh
```

This builds the apps and starts:
Kafka, PostgreSQL, Keycloak, APISIX, Portal Backend, and Config Adapter.

---

## Run Individual Services

All services share a Docker network. Create it first:

```bash
docker network create civitas-network
```

Then start the services:

```bash
cd kafka     && docker compose up -d
cd postgres  && docker compose up -d
cd keycloak  && docker compose up -d
cd apisix    && docker compose up -d
cd frost     && docker compose up -d
```

### FROST APISIX Route Setup

When running services individually, you need to configure the APISIX route for FROST manually:

```bash
cd frost
./setup-apisix-route.sh
```

This sets up API key authentication for FROST through APISIX. After setup:
- FROST is accessible at: `http://localhost:9080/FROST-Server/v1.1`
- API Key header: `X-API-Key`
- API Key value: `dev-frost-api-key`

Test with:
```bash
curl -H 'X-API-Key: dev-frost-api-key' http://localhost:9080/FROST-Server/v1.1
```

> **Note:** The `start-portal-dev.sh` script runs this automatically.

---

## Key URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Portal Frontend | http://localhost:3000 | dev@civitas.local / dev123 |
| Portal Backend | http://localhost:8089 | - |
| Config Adapter | http://localhost:8088 | - |
| Keycloak Admin | http://localhost:8080 | admin / admin |
| Kafka UI | http://localhost:8090 | - |
| FROST Server | http://localhost:1883 | - |
| APISIX Gateway | http://localhost:9080 | - |

### Default Development User

A default user is automatically created in Keycloak for development:

- **Email/Username:** `dev@civitas.local`
- **Password:** `dev123`

Use these credentials to log in to the Portal Frontend.

---

## Common Commands

```bash
docker compose up --build
docker compose down
docker compose down -v
docker compose logs -f
docker compose ps
```

---