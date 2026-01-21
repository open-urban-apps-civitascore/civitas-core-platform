# CIVITAS CORE – Development Environment

Docker Compose setup for running the **CIVITAS CORE Platform** locally.
You can run the **full backend stack** (recommended) or **individual services**.

---

## Structure

```
dev-environment/
├── backend/    # Full backend stack (recommended)
├── kafka/      # Kafka only
├── postgres/   # PostgreSQL only
├── keycloak/   # Keycloak only
└── apisix/     # API Gateway only
```

---

## Prerequisites

* Docker + Docker Compose v2
* Java 21+
* Maven 3.6+

---

## Quick Start (Recommended)

Start everything:

```bash
cd backend
./start.sh
```

This builds the apps and starts:
Kafka, PostgreSQL, Keycloak, APISIX, Portal Backend, and Config Adapter.

---

## Run Individual Services

```bash
cd kafka     && docker compose up
cd postgres  && docker compose up
cd keycloak  && docker compose up
cd apisix    && docker compose up
```

---

## Key URLs

* Kafka UI: [http://localhost:8090](http://localhost:8090)
* Keycloak: [http://localhost:8080](http://localhost:8080) (admin / admin)
* Portal Backend: [http://localhost:8089](http://localhost:8089)
* Config Adapter: [http://localhost:8088](http://localhost:8088)
* APISIX Gateway: [http://localhost:9080](http://localhost:9080)

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