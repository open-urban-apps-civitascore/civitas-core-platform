# GeoServer Cloud Development Setup

GeoServer Cloud — a microservices-based GeoServer distribution — with a shared
PostgreSQL backend (pgconfig catalog + ACL) for local development.

---

## Architecture

GeoServer Cloud splits GeoServer into independent microservices that communicate
via RabbitMQ and read the shared catalog directly from PostgreSQL. All OGC
service requests enter through the gateway. The stack runs the `standalone`
profile production uses, which means no service discovery and no config server.

```
Host                Docker (geoserver-internal and civitas-network)
────                ────────────────────────────────────────────────
localhost:8082 ──►  gateway (civitas-geoserver)
                      ├─► webui
                      ├─► wfs
                      ├─► wms
                      ├─► wcs
                      ├─► wps
                      ├─► restconfig
                      └─► gwc
                    acl  ◄──► postgis-dev-env (database: geoserver)
                    rabbitmq (message bus)
localhost:5432 ──►  postgis-dev-env (PostGIS)
```

The gateway is also connected to `civitas-network` so APISIX can route
`/geoserver/*` requests to it by Docker hostname (`civitas-geoserver:8080`).

---

## Services

| Container | Role                                | Local Port |
|-----------|-------------------------------------|------------|
| `civitas-geoserver` | Gateway — single entry point        | `8082` |
| `postgis-dev-env` | Shared PostgreSQL/PostGIS (pgconfig + ACL database) | `5432` |
| *(internal)* | OWS, WCS, WPS, REST, WebUI, GWC     | — |
| *(internal)* | RabbitMQ, ACL                       | — |

---

## Quick Start

```bash
cd dev-environment/geoserver
cp .env.example .env          # review GEOSERVER_DB_PASSWORD
cd ../postgres && docker compose up -d
cd ../geoserver
docker compose up -d
```

Or start everything via the root script:

```bash
cd dev-environment
./start-portal-dev.sh
```

GeoServer Cloud takes up to a minute to fully start (each microservice runs its
catalog migrations and connects to the message bus before answering requests).

---

## Connection Properties

### PostGIS — Direct Access (host machine / DBeaver / psql)

| Property | Value |
|----------|-------|
| Host | `localhost` |
| Port | `5432` |
| Database | `geoserver` |
| User | `geoserver` |
| Password | see `geoserver/.env` → `GEOSERVER_DB_PASSWORD` |
| SSL | disabled (local dev) |

```bash
psql -h localhost -p 5432 -U geoserver -d geoserver
```

### PostGIS — From GeoServer Containers (Docker network)

GeoServer datastores must use the internal Docker hostname:

| Property | Value |
|----------|-------|
| Host | `postgis-dev-env` |
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

| Service | URL                                                                                   |
|---------|---------------------------------------------------------------------------------------|
| WFS GetCapabilities | `http://localhost:9080/geoserver/{workspace}/ows?SERVICE=WFS&REQUEST=GetCapabilities` |
| WFS direct | `http://localhost:8082/geoserver/{workspace}/wfs`                                     |
| WMS direct | `http://localhost:8082/geoserver/{workspace}/wms`                                     |

### GeoServer Admin UI

```
http://localhost:8082/geoserver/web
User:     admin
Password: geoserver
```

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
          {"@key": "host",     "content": "postgis-dev-env"},
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
  `geoserver` database on the shared PostGIS server (`pgconfig` schema), not in
  a file-based data directory. The `postgres_portal_data` volume stores it.
- GeoWebCache tiles are stored in the `geowebcache_data` volume.
- The shared PostGIS server publishes port `5432`. GeoServer uses the logical
  `geoserver` database on that server.
- Spatial tables must exist in the PostGIS database before they can be
  published as feature types. The shared database init scripts seed a test schema.
- The ACL authorization layer is **not enabled** (`acl` Spring profile omitted).
  OGC services (WFS, WMS, WCS) are publicly accessible without authentication,
  matching the default behaviour of monolithic GeoServer. The REST API and Web
  UI still require admin credentials.
