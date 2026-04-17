# GeoServer Development Setup

GeoServer with a dedicated PostGIS database for local development of GeoServer integration.

---

## Services

| Container | Role | Local Port |
|-----------|------|------------|
| `civitas-geoserver` | GeoServer OGC server | `8082` |
| `civitas-geoserver-db` | PostGIS database | `5434` |

The PostGIS database is only reachable from within the `geoserver-network`
by the GeoServer container, and directly from the host on port `5434`.
It is intentionally **not** connected to `civitas-network` — spatial data is
separate from the portal backend database.

---

## Quick Start

```bash
cd dev-environment/geoserver
cp .env.example .env          # set passwords
docker compose up -d
```

Or start everything via the root script:

```bash
cd dev-environment
./start-portal-dev.sh
```

---

## Connection Properties

### PostGIS — Direct Access (host machine / DBeaver / psql)

| Property | Value |
|----------|-------|
| Host | `localhost` |
| Port | `5434` |
| Database | `geoserver` |
| User | `geoserver` |
| Password | see `geoserver/.env` → `POSTGIS_PASSWORD` |
| SSL | disabled (local dev) |

```bash
psql -h localhost -p 5434 -U geoserver -d geoserver
```

### PostGIS — From GeoServer Container (Docker network)

GeoServer datastores must use the internal Docker hostname:

| Property | Value |
|----------|-------|
| Host | `civitas-geoserver-db` |
| Port | `5432` |
| Database | `geoserver` |
| User | `geoserver` |
| Password | see `geoserver/.env` → `POSTGIS_PASSWORD` |

### GeoServer REST API (config-adapter / curl)

| Property | Value |
|----------|-------|
| Base URL | `http://localhost:8082/geoserver` |
| Admin User | `admin` (or `GEOSERVER_ADMIN_USER` from `.env`) |
| Admin Password | see `geoserver/.env` → `GEOSERVER_ADMIN_PASSWORD` |
| Auth scheme | HTTP Basic |

```bash
# Test REST API connectivity
curl -u admin:<password> http://localhost:8082/geoserver/rest/workspaces.json
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
Password: see geoserver/.env → GEOSERVER_ADMIN_PASSWORD
```

---

## Configuring a GeoServer Datastore via REST API

The config-adapter will create datastores via the GeoServer REST API.
Equivalent manual steps for development/debugging:

**1. Create workspace**
```bash
curl -u admin:<pw> -X POST http://localhost:8082/geoserver/rest/workspaces \
  -H "Content-Type: application/json" \
  -d '{"workspace": {"name": "my-dataset"}}'
```

**2. Create PostGIS datastore**
```bash
curl -u admin:<pw> -X POST \
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
          {"@key": "passwd",   "content": "<POSTGIS_PASSWORD>"},
          {"@key": "dbtype",   "content": "postgis"},
          {"@key": "schema",   "content": "public"}
        ]
      }
    }
  }'
```

**3. Publish a feature type (implicitly creates the Layer)**
```bash
curl -u admin:<pw> -X POST \
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
curl -u admin:<pw> -X DELETE \
  "http://localhost:8082/geoserver/rest/workspaces/my-dataset?recurse=true"
```

---

## Notes

- The PostGIS container exposes port `5434` on the host to avoid conflict with
  the portal backend postgres on `5432`.
- GeoServer configuration (workspaces, stores, layers) is persisted in the
  `geoserver_data` Docker volume — it survives container restarts.
- Spatial tables must exist in the PostGIS database before they can be
  published as feature types.
