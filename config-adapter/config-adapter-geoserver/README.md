# GeoServer Config Adapter

Production-ready adapter for managing [GeoServer](https://geoserver.org/) OGC geo service configuration through CloudEvents.

## Overview

The GeoServer adapter integrates with the [GeoServer REST API](https://docs.geoserver.org/stable/en/user/rest/) to manage geo service entities. It consumes CloudEvents from Kafka and translates them into GeoServer REST API calls, enabling automated provisioning of workspaces, datastores, feature types, layers, and styles.

**Key Features:**
- Full CRUD operations for GeoServer resources (workspaces, datastores, feature types, layers, styles)
- Typed configuration model per resource type with `toApiMap()` producing the exact GeoServer REST body
- HTTP Basic Auth for the GeoServer management REST API (data access secured by APISIX + OPA upstream)
- Automatic `?recurse=true` on deletes of workspaces, datastores, coverage stores, feature types, and coverages
- Idempotent operations: HTTP 409 on CREATE and HTTP 404 on DELETE are treated as success
- Asynchronous result publishing via CloudEvents
- Saga command handler for atomic workspace provisioning (PROVISION_WORKSPACE)

## Architecture

```text
┌──────────────────────────┐
│    Kafka Topics          │
│  - geo.workspace.*       │
│  - geo.datastore.*       │
│  - geo.featuretype.*     │
│  - geo.layer.*           │
│  - geo.style.*           │
└───────────┬──────────────┘
            │ CloudEvents
            ↓
┌───────────────────────────────┐
│    GeoServerAdapter           │
│  - JAX-RS Client              │
│  - Basic Auth                 │
│  - Typed config models        │
│  - Error Handling             │
└───────────┬───────────────────┘
            │ REST API (/rest)
            ↓
┌────────────────────────────────┐
│   GeoServer (Cloud)            │
│  - Workspace management        │
│  - PostGIS Datastore           │
│  - Feature Types (WFS)         │
│  - Layers (WMS)                │
│  - Styles (SLD)                │
└────────────┬───────────────────┘
             │
┌────────────▼───────────────────┐
│  Data Access (not this adapter)│
│  - APISIX (routing)            │
│  - OPA (authorization)         │
└────────────────────────────────┘
```

> **Authentication boundary:** This adapter authenticates to the GeoServer **management REST API**
> using Basic Auth. WFS/WMS data endpoints are protected separately by APISIX and OPA in front
> of GeoServer — that is outside the scope of this adapter.

## Supported Operations

### Resource Types

| Resource Type | CREATE | UPDATE | DELETE | GeoServer REST path |
|---------------|:------:|:------:|:------:|---------------------|
| Workspace     | ✅ | ✅ | ✅ | `/rest/workspaces` |
| Datastore     | ✅ | ✅ | ✅ | `/rest/workspaces/{ws}/datastores` |
| Feature Type  | ✅ | ✅ | ✅ | `/rest/workspaces/{ws}/datastores/{ds}/featuretypes` |
| Layer         | ❌ | ✅ | ✅ | `/rest/layers` or `/rest/workspaces/{ws}/layers` (created implicitly with its feature type) |
| Style         | ✅ | ✅ | ✅ | `/rest/styles` or `/rest/workspaces/{ws}/styles` |

The `targetResource` in the config event maps directly to the REST path relative to `/rest/`. Examples:

| Operation | `targetResource` | GeoServer REST call |
|-----------|-----------------|---------------------|
| CREATE workspace | `workspaces` | `POST /rest/workspaces` |
| UPDATE workspace | `workspaces/myws` | `PUT /rest/workspaces/myws` |
| DELETE workspace | `workspaces/myws` | `DELETE /rest/workspaces/myws?recurse=true` |
| CREATE datastore | `workspaces/myws/datastores` | `POST /rest/workspaces/myws/datastores` |
| CREATE featuretype | `workspaces/myws/datastores/myds/featuretypes` | `POST /rest/…/featuretypes` |
| UPDATE featuretype | `workspaces/myws/datastores/myds/featuretypes/myft` | `PUT /rest/…/featuretypes/myft` |
| CREATE style | `styles` | `POST /rest/styles` |

### Subscribed Topics (14 Topics)

**Workspace Events (3):**
- `de.civitascore.geo.workspace.created`
- `de.civitascore.geo.workspace.updated`
- `de.civitascore.geo.workspace.deleted`

**Datastore Events (3):**
- `de.civitascore.geo.datastore.created`
- `de.civitascore.geo.datastore.updated`
- `de.civitascore.geo.datastore.deleted`

**Feature Type Events (3):**
- `de.civitascore.geo.featuretype.created`
- `de.civitascore.geo.featuretype.updated`
- `de.civitascore.geo.featuretype.deleted`

**Layer Events (2):**
- `de.civitascore.geo.layer.updated`
- `de.civitascore.geo.layer.deleted`

> No `layer.created`: GeoServer has no POST on `/layers`; a layer is created implicitly when its feature type (or coverage) is published.

**Style Events (3):**
- `de.civitascore.geo.style.created`
- `de.civitascore.geo.style.updated`
- `de.civitascore.geo.style.deleted`

## Configuration

### Properties

```properties
# GeoServer REST API base URL (optional, default: http://localhost:8080/geoserver)
geoserver.url=http://localhost:8080/geoserver

# Public GeoServer URL for saga results (optional, defaults to geoserver.url)
# Use when GeoServer is reachable at a different URL from downstream consumers.
geoserver.public.url=https://geo.example.com/geoserver

# Topics to subscribe to (comma-separated, required)
geoserver.topics=de.civitascore.geo.workspace.created,de.civitascore.geo.workspace.updated,...

# Admin credentials for the GeoServer management REST API (required)
geoserver.admin.user=admin
geoserver.admin.password=geoserver

# PostGIS connection parameters for the saga handler (required for PROVISION_WORKSPACE)
geoserver.postgis.host=localhost
geoserver.postgis.port=5432
geoserver.postgis.database=civitas_geo
geoserver.postgis.schema=public
geoserver.postgis.user=geo_user
geoserver.postgis.password=secret
```

### Encrypted credentials

Passwords (`geoserver.admin.password`, `geoserver.postgis.password`, and the `passwd` of an
incoming `DataStoreConfig`) may be supplied encrypted as `ENC(<base64>)` values, following the same
AES-256-GCM scheme as the RedPanda adapter. They are decrypted in-memory only when needed; plaintext
values are accepted unchanged for backward compatibility. The master key is read from the
`CIVITAS_MASTER_KEY` environment variable, and encrypted values must be produced with the
`portal-backend:datasource-connector` credential context. If `CIVITAS_MASTER_KEY` is not set,
`ENC(...)` values cannot be decrypted (a warning is logged at startup).

### Environment Variables

All properties can be overridden with environment variables (dots → underscores, uppercase):

```bash
GEOSERVER_URL=http://geoserver:8080/geoserver
GEOSERVER_PUBLIC_URL=https://geo.example.com/geoserver
GEOSERVER_TOPICS=de.civitascore.geo.workspace.created,...
GEOSERVER_ADMIN_USER=admin
GEOSERVER_ADMIN_PASSWORD=secret
GEOSERVER_POSTGIS_HOST=postgres
GEOSERVER_POSTGIS_PORT=5432
GEOSERVER_POSTGIS_DATABASE=civitas_geo
GEOSERVER_POSTGIS_USER=geo_user
GEOSERVER_POSTGIS_PASSWORD=secret
```

### Docker Compose Example

```yaml
services:
  config-adapter:
    image: config-adapter:latest
    environment:
      ADAPTERS: geoserver
      EVENTHANDLER_NAME: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      GEOSERVER_URL: http://geoserver:8080/geoserver
      GEOSERVER_ADMIN_USER: admin
      GEOSERVER_ADMIN_PASSWORD: ${GEOSERVER_ADMIN_PASSWORD}
      GEOSERVER_TOPICS: de.civitascore.geo.workspace.created,...
      GEOSERVER_POSTGIS_HOST: postgres
      GEOSERVER_POSTGIS_DATABASE: civitas_geo
      GEOSERVER_POSTGIS_USER: geo_user
      GEOSERVER_POSTGIS_PASSWORD: ${POSTGIS_PASSWORD}
```

## Configuration Model

Each resource type has a dedicated typed model class in `config-adapter-api`. The `resourceType`
field in the event payload is used by Jackson as a discriminator to select the correct class.

### WorkspaceConfig (`resourceType: "geoserver-workspace"`)

```json
{
  "resourceType": "geoserver-workspace",
  "name": "civitas_dataset1",
  "isolated": false
}
```

Produces: `{"workspace": {"name": "civitas_dataset1", "isolated": false}}`

### DataStoreConfig (`resourceType: "geoserver-datastore"`)

```json
{
  "resourceType": "geoserver-datastore",
  "name": "civitas_postgis",
  "description": "PostGIS data source",
  "type": "PostGIS",
  "enabled": true,
  "host": "postgres",
  "port": "5432",
  "database": "civitas_geo",
  "schema": "public",
  "user": "geo_user",
  "passwd": "secret",
  "dbtype": "postgis",
  "exposePrimaryKeys": true
}
```

Connection parameters are serialized as a GeoServer entry-list in `toApiMap()`.

### FeatureTypeConfig (`resourceType: "geoserver-featuretype"`)

```json
{
  "resourceType": "geoserver-featuretype",
  "name": "traffic_counts",
  "nativeName": "traffic_counts",
  "title": "Traffic Counts",
  "abstract": "Traffic counting data per hour",
  "srs": "EPSG:4326",
  "projectionPolicy": "REPROJECT_TO_DECLARED",
  "enabled": true,
  "nativeBoundingBox": {
    "minx": -180.0, "maxx": 180.0,
    "miny":  -90.0, "maxy":  90.0,
    "crs": "EPSG:4326"
  }
}
```

Valid `projectionPolicy` values: `NONE`, `REPROJECT_TO_DECLARED`, `FORCE_DECLARED`.

### LayerConfig (`resourceType: "geoserver-layer"`)

```json
{
  "resourceType": "geoserver-layer",
  "name": "traffic_counts",
  "title": "Traffic Counts",
  "type": "VECTOR",
  "defaultStyle": "traffic_style",
  "enabled": true,
  "queryable": true
}
```

The `defaultStyle` string is automatically wrapped in `{"name": "..."}` by `toApiMap()`.
Valid `type` values: `VECTOR`, `RASTER`, `REMOTE`, `WMS`, `GROUP`.

### StyleConfig (`resourceType: "geoserver-style"`)

```json
{
  "resourceType": "geoserver-style",
  "name": "traffic_style",
  "filename": "traffic_style.sld",
  "format": "sld",
  "languageVersion": "1.0.0",
  "workspace": "civitas_dataset1"
}
```

`languageVersion` is wrapped as `{"version": "..."}` and `workspace` as `{"name": "..."}` in
`toApiMap()`. Omit `workspace` for global styles.

> **Note:** This model creates the style metadata record. Uploading the SLD document body
> requires a separate call to the GeoServer REST API — outside the scope of this adapter's
> current implementation.

## Event Format

### Input Event (CloudEvent)

#### Workspace Create Example

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.geo.workspace.created",
  "source": "civitas.dataset.provisioning",
  "id": "event-123",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-456",
      "timestamp": "2026-01-15T10:00:00Z",
      "source": "dataset.service",
      "correlationId": "corr-789",
      "configVersion": "1.0",
      "resultTopic": "de.civitascore.geo.processing.result"
    },
    "payload": {
      "targetComponent": "geoserver",
      "targetResource": "workspaces",
      "operation": "CREATE",
      "config": {
        "value": {
          "resourceType": "geoserver-workspace",
          "name": "civitas_dataset1"
        }
      }
    }
  }
}
```

#### Feature Type Create Example

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.geo.featuretype.created",
  "source": "civitas.dataset.provisioning",
  "id": "event-456",
  "data": {
    "metadata": {
      "messageId": "msg-789",
      "correlationId": "corr-012",
      "resultTopic": "de.civitascore.geo.processing.result"
    },
    "payload": {
      "targetComponent": "geoserver",
      "targetResource": "workspaces/civitas_dataset1/datastores/civitas_postgis/featuretypes",
      "operation": "CREATE",
      "config": {
        "value": {
          "resourceType": "geoserver-featuretype",
          "name": "traffic_counts",
          "nativeName": "traffic_counts",
          "title": "Traffic Counts",
          "srs": "EPSG:4326",
          "projectionPolicy": "REPROJECT_TO_DECLARED",
          "enabled": true
        }
      }
    }
  }
}
```

#### Workspace Delete Example

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.geo.workspace.deleted",
  "source": "civitas.dataset.provisioning",
  "id": "event-789",
  "data": {
    "metadata": {
      "messageId": "msg-012",
      "correlationId": "corr-345",
      "resultTopic": "de.civitascore.geo.processing.result"
    },
    "payload": {
      "targetComponent": "geoserver",
      "targetResource": "workspaces/civitas_dataset1",
      "operation": "DELETE",
      "config": {
        "value": {
          "resourceType": "geoserver-workspace"
        }
      }
    }
  }
}
```

The adapter adds `?recurse=true` automatically for workspace and datastore deletes.

### Output Event (Result)

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.geo.processing.result",
  "source": "de.civitascore.config-adapter.geoserver",
  "id": "result-123",
  "datacontenttype": "application/json",
  "correlationid": "corr-789",
  "originalmessageid": "msg-456",
  "status": "SUCCESS",
  "operation": "CREATE",
  "targetresource": "workspaces",
  "resourceid": "civitas_dataset1",
  "data": {
    "correlationId": "corr-789",
    "originalMessageId": "msg-456",
    "status": "SUCCESS",
    "message": "GeoServer WORKSPACE created successfully",
    "resourceId": "civitas_dataset1",
    "operation": "CREATE",
    "targetResource": "workspaces",
    "timestamp": "2026-01-15T10:00:01Z",
    "source": "de.civitascore.config-adapter.geoserver"
  }
}
```

## Saga Handler

The `GeoServerSagaHandler` provides atomic workspace provisioning for the dataset lifecycle saga.

> **Note:** The saga handler is intentionally minimal. The full saga orchestration will be
> replaced by Flowable. Only the operations needed by the current dataset lifecycle are implemented.

### Operations

| Operation | Description | Compensation |
|-----------|-------------|-------------|
| `PROVISION_WORKSPACE` | Creates workspace + PostGIS datastore + feature types | `DELETE_WORKSPACE` |
| `UPDATE_WORKSPACE` | Creates/updates feature types; captures state for rollback | `RESTORE_WORKSPACE` |
| `DELETE_WORKSPACE` | Deletes workspace recursively (`?recurse=true`) | — |
| `RESTORE_WORKSPACE` | Restores previous feature type state | — |

### Saga Topics

```text
de.civitascore.dataset.geoserver.execute
de.civitascore.dataset.geoserver.compensate
de.civitascore.dataset.geoserver.result
```

### PROVISION_WORKSPACE Payload

```json
{
  "datasetId": "ds-abc-123",
  "datasetName": "Traffic Counts Dataset",
  "datasinks": [
    {
      "type": "POSTGIS",
      "configuration": {
        "tableName": "traffic_counts",
        "primaryKey": "id",
        "geometryColumn": "geom",
        "geometryType": "Point",
        "crs": "EPSG:4326"
      }
    }
  ]
}
```

`PROVISION_WORKSPACE` derives the workspace name from `datasetId` via `toWorkspaceName()`:
lowercases and replaces all non-alphanumeric/non-underscore characters with `_`.

Only datasinks with `"type": "POSTGIS"` are provisioned as feature types; other sink types
are skipped. From each sink's `configuration`, only `tableName`, `crs`, and `projectionPolicy`
(default `REPROJECT_TO_DECLARED`) are forwarded — fields such as `primaryKey` and `geometryColumn`
are derived by GeoServer from the PostGIS table and intentionally not sent. `UPDATE_WORKSPACE`
upserts (PUT on HTTP 409) so existing feature types are actually updated, and its compensation
(`RESTORE_WORKSPACE`) deletes feature types created during the update before restoring the previous
state.

### PROVISION_WORKSPACE Result

```json
{
  "workspaceName": "ds_abc_123",
  "wfsUrl": "https://geo.example.com/geoserver/ds_abc_123/wfs",
  "wmsUrl": "https://geo.example.com/geoserver/ds_abc_123/wms"
}
```

## Error Handling

### HTTP Status Code Mapping

| HTTP Status | Operation | Behavior |
|-------------|-----------|----------|
| 2xx | Any | Success |
| 409 Conflict | CREATE | **Idempotent success** — resource already exists |
| 404 Not Found | DELETE | **Idempotent success** — resource already deleted |
| Other 4xx | Any | `FatalAdapterException` → DLQ |
| 5xx | Any | `RetryableAdapterException` → exponential backoff retry |
| Network error | Any | `RetryableAdapterException` → exponential backoff retry |

### GeoServer-Specific Error Codes

| Code | Name | Retryable | Description |
|------|------|-----------|-------------|
| 3401 | `GEOSERVER_ERROR` | Yes | GeoServer service error (HTTP 5xx) |
| 3402 | `GEOSERVER_RESOURCE_ERROR` | No | Resource operation failed (HTTP 4xx) |

## Testing

### Unit Tests

```bash
mvn test -pl config-adapter-geoserver
```

Tests cover:
- Initialization (credentials required, missing credentials throws)
- Resource type detection from path
- Resource name extraction and collection path detection
- `?recurse=true` flag for workspace and datastore deletes
- Successful create/update/delete operations for all entity types
- HTTP error handling (4xx → fatal, 5xx → retryable)
- Network error handling
- Result event publishing
- All typed model `toApiMap()` outputs (workspace, datastore, feature type, layer, style, bounding box)
- Saga handler: PROVISION_WORKSPACE, DELETE_WORKSPACE, RESTORE_WORKSPACE, unknown operations

### Integration Tests

`GeoServerAdapterIntegrationTest` runs the adapter against a real **GeoServer Cloud
2.28.3.0** stack started with Testcontainers. The setup mirrors
[`dev-environment/geoserver/docker-compose.yaml`](../../dev-environment/geoserver/docker-compose.yaml)
and exercises the same provisioning flow as the Bruno collection
(`dev-environment/geoserver/bruno/`).

**Stack started per JVM (singleton container pattern):**

| Container | Image | Purpose |
|-----------|-------|---------|
| `geoserverdb` (alias: `geodatabase`) | `imresamu/postgis:17-3.5` | pgconfig catalog + spatial data, seeded with `dataset_test_uuid_001` schema |
| `rabbitmq` | `rabbitmq:3.13.3-alpine` | Spring Cloud Bus |
| `discovery` | `geoservercloud/geoserver-cloud-discovery:2.28.3.0` | Service discovery (Consul/Eureka) |
| `config` | `geoservercloud/geoserver-cloud-config:2.28.3.0` | Spring Cloud Config server (port 8080) |
| `restconfig` | `geoservercloud/geoserver-cloud-rest:2.28.3.0` | GeoServer REST API under test |

Total stack startup: ~60–120s.

**Scenarios covered:**

- `provisionWorkspaceDatastoreAndFeatureTypesEndToEnd` — workspace → PostGIS datastore →
  point + polygon feature types → update feature type title (Bruno steps 01–04)
- `duplicateWorkspaceCreateIsIdempotent409` — HTTP 409 on duplicate CREATE returns SUCCESS
- `deleteMissingWorkspaceIsIdempotent404` — HTTP 404 on missing DELETE returns SUCCESS
- `deleteWorkspaceUsesRecurseTrue` — workspace with child datastore deleted recursively

> **Style creation is not exercised end-to-end:** the adapter sends a JSON metadata body,
> but GeoServer requires an accompanying SLD XML body uploaded as
> `application/vnd.ogc.sld+xml` (see Bruno step 05). SLD body upload is out of scope for
> the adapter's current implementation. Unit tests still verify the JSON body shape.

```bash
# Run only the integration test (requires Docker)
mvn test -pl config-adapter-geoserver -Dtest=GeoServerAdapterIntegrationTest
```

## Troubleshooting

### Connection Refused

```text
Network error during GeoServer resource creation: Connection refused
```

Verify GeoServer is running and `geoserver.url` is correct.

### Unauthorized (401)

```text
GeoServer client error during GeoServer resource creation: HTTP 401
```

Verify `geoserver.admin.user` and `geoserver.admin.password` match the GeoServer admin credentials.

### Bad Request (400)

```text
GeoServer client error during GeoServer resource creation: HTTP 400
```

Validate the configuration model fields against the [GeoServer REST API documentation](https://docs.geoserver.org/stable/en/user/rest/).
Ensure required fields (e.g., `name` for workspaces, `nativeName` + `srs` for feature types) are present.

### Resource Not Found (404) on UPDATE/DELETE

```text
GeoServer client error during GeoServer resource update: HTTP 404
```

For UPDATE/DELETE operations, ensure the resource exists. Check that the `targetResource` path
and resource name are correct. Note: 404 on DELETE is treated as idempotent success.

## Resources

- [GeoServer REST API Documentation](https://docs.geoserver.org/stable/en/user/rest/)
- [GeoServer Cloud](https://github.com/geoserver/geoserver-cloud)
- [OGC WFS Standard](https://www.ogc.org/standards/wfs)
- [OGC WMS Standard](https://www.ogc.org/standards/wms)
- [CloudEvents Specification](https://cloudevents.io/)

## License

European Union Public License (EU-PL) 1.2
