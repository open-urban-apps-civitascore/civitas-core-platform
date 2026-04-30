# GeoServer Cloud Development Setup

GeoServer Cloud — a microservices-based GeoServer distribution — with a shared
PostgreSQL backend (pgconfig catalog + ACL) for local development.

---

## Architecture

GeoServer Cloud splits GeoServer into independent microservices that communicate
via RabbitMQ and register with a Consul discovery service. All OGC service
requests enter through the gateway.

```
Host                Docker (geoserver-internal network)
────                ──────────────────────────────────
localhost:8082 ──►  gateway (civitas-geoserver)
                      ├─► webui
                      ├─► wfs
                      ├─► wms
                      ├─► wcs
                      ├─► wps
                      ├─► restconfig
                      └─► gwc
                    acl  ◄──► geoserverdb (PostgreSQL/PostGIS)
                    config (Spring Cloud Config)
                    discovery (Consul)
                    rabbitmq (message bus)
localhost:5434 ──►  geoserverdb (direct DB access)
localhost:8500 ──►  discovery (Consul UI)
```

The gateway is also connected to `civitas-network` so APISIX can route
`/geoserver/*` requests to it by Docker hostname (`civitas-geoserver:8080`).

---

## Services

| Container | Role | Local Port |
|-----------|------|------------|
| `civitas-geoserver` | Gateway — single entry point | `8082` |
| `civitas-geoserver-db` | PostgreSQL/PostGIS (pgconfig + ACL) | `5434` |
| *(internal)* | Consul discovery UI | `8500` |
| *(internal)* | WFS, WMS, WCS, WPS, REST, WebUI, GWC | — |
| *(internal)* | RabbitMQ, ACL, Config server | — |

---

## Quick Start

```bash
cd dev-environment/geoserver
cp .env.example .env          # review GEOSERVER_DB_PASSWORD
docker compose up -d
```

Or start everything via the root script:

```bash
cd dev-environment
./start-portal-dev.sh
```

GeoServer Cloud takes ~60–90 seconds to fully start (all microservices must
register with Consul before the gateway begins routing).

---

## Connection Properties

### PostGIS — Direct Access (host machine / DBeaver / psql)

| Property | Value |
|----------|-------|
| Host | `localhost` |
| Port | `5434` |
| Database | `geoserver` |
| User | `geoserver` |
| Password | see `geoserver/.env` → `GEOSERVER_DB_PASSWORD` |
| SSL | disabled (local dev) |

```bash
psql -h localhost -p 5434 -U geoserver -d geoserver
```

### PostGIS — From GeoServer Containers (Docker network)

GeoServer datastores must use the internal Docker hostname:

| Property | Value |
|----------|-------|
| Host | `civitas-geoserver-db` |
| Port | `5432` |
| Database | `geoserver` |
| User | `geoserver` |
| Password | see `geoserver/.env` → `GEOSERVER_DB_PASSWORD` |

### GeoServer REST API (config-adapter / curl)

| Property | Value |
|----------|-------|
| Base URL | `http://localhost:8082/geoserver` |
| Admin User | `admin` |
| Admin Password | `geoserver` (GeoServer Cloud default) |
| Auth scheme | HTTP Basic |

```bash
# Test REST API connectivity
curl -u admin:geoserver http://localhost:8082/geoserver/rest/workspaces.json
```

### GeoServer OGC Services (via APISIX gateway)

| Service | URL |
|---------|-----|
| WFS GetCapabilities | `http://localhost:9080/geoserver/{workspace}/wfs?SERVICE=WFS&REQUEST=GetCapabilities` |
| WMS GetCapabilities | `http://localhost:9080/geoserver/{workspace}/wms?SERVICE=WMS&REQUEST=GetCapabilities` |
| WFS direct | `http://localhost:8082/geoserver/{workspace}/wfs` |
| WMS direct | `http://localhost:8082/geoserver/{workspace}/wms` |

### GeoServer Admin UI

```
http://localhost:8082/geoserver/web
User:     admin
Password: geoserver
```

### Consul Service Discovery UI

```
http://localhost:8500
```

---

## Configuring a GeoServer Datastore via REST API

The config-adapter creates datastores via the GeoServer REST API.
Equivalent manual steps for development/debugging:

**1. Create workspace**
```bash
curl -u admin:geoserver -X POST http://localhost:8082/geoserver/rest/workspaces \
  -H "Content-Type: application/json" \
  -d '{"workspace": {"name": "my-dataset"}}'
```

**2. Create PostGIS datastore**
```bash
curl -u admin:geoserver -X POST \
  http://localhost:8082/geoserver/rest/workspaces/my-dataset/datastores \
  -H "Content-Type: application/json" \
  -d '{
    "dataStore": {
      "name": "my-dataset-db",
      "connectionParameters": {
        "entry": [
          {"@key": "host",     "content": "civitas-geoserver-db"},
          {"@key": "port",     "content": "5432"},
          {"@key": "database", "content": "geoserver"},
          {"@key": "user",     "content": "geoserver"},
          {"@key": "passwd",   "content": "<GEOSERVER_DB_PASSWORD>"},
          {"@key": "dbtype",   "content": "postgis"},
          {"@key": "schema",   "content": "public"}
        ]
      }
    }
  }'
```

**3. Publish a feature type (implicitly creates the Layer)**
```bash
curl -u admin:geoserver -X POST \
  http://localhost:8082/geoserver/rest/workspaces/my-dataset/datastores/my-dataset-db/featuretypes \
  -H "Content-Type: application/json" \
  -d '{
    "featureType": {
      "name": "my_table",
      "nativeName": "my_table",
      "title": "My Layer",
      "srs": "EPSG:4326"
    }
  }'
```

> **Note:** `POST .../featuretypes` creates both the FeatureType (WFS schema)
> and the Layer (WMS-publishable view) in a single call. There is no separate
> "create layer" endpoint — layer creation is a side effect of feature type
> publication.

**4. Delete workspace (including all child resources)**
```bash
curl -u admin:geoserver -X DELETE \
  "http://localhost:8082/geoserver/rest/workspaces/my-dataset?recurse=true"
```

---

## Notes

- GeoServer Cloud persists catalog data (workspaces, stores, layers) in the
  `geoserverdb` PostgreSQL database (`pgconfig` schema), not in a file-based
  data directory. The `geoserverdb_data` volume survives container restarts.
- GeoWebCache tiles are stored in the `geowebcache_data` volume.
- The PostGIS container exposes port `5434` to avoid conflict with the portal
  backend PostgreSQL on `5432`.
- Spatial tables must exist in the PostGIS database before they can be
  published as feature types. The `init/` directory seeds a test schema on
  first startup.
- The ACL service manages access control for GeoServer resources. Default
  admin credentials for the ACL API are `admin` / `s3cr3t` (dev only).
