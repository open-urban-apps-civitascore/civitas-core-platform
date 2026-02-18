# FROST Config Adapter

Production-ready adapter for managing [FROST-Server](https://github.com/FraunhoferIOSB/FROST-Server) SensorThings API configuration through CloudEvents.

## Overview

The FROST adapter integrates with FROST-Server's OGC SensorThings API to manage IoT sensor entities. It consumes CloudEvents from Kafka and translates them into SensorThings API calls, enabling automated configuration management for your IoT sensor infrastructure.

**Key Features:**
- Full CRUD operations for SensorThings entities (Things, Locations, Sensors, ObservedProperties, Datastreams)
- FROST Projects extension support (project-scoped entity management)
- Asynchronous result publishing via CloudEvents
- Comprehensive error handling with HTTP status-based categorization
- Production-ready with integration tests
- Testcontainers-based testing with real FROST-Server instances

## Architecture

```
┌─────────────────────────┐
│    Kafka Topics         │
│  - thing.*              │
│  - location.*           │
│  - sensor.*             │
│  - observedproperty.*   │
│  - datastream.*         │
│  - project.*            │
└───────────┬─────────────┘
            │ CloudEvents
            ↓
┌───────────────────────────┐
│    FrostAdapter           │
│  - JAX-RS Client          │
│  - Event Processing       │
│  - Project Scoping        │
│  - Error Handling         │
└───────────┬───────────────┘
            │ REST API (8080)
            ↓
┌───────────────────────────┐
│   FROST-Server            │
│  - Things Management      │
│  - Locations Management   │
│  - Sensors Management     │
│  - Datastreams Management │
│  - Projects Extension     │
└───────────────────────────┘
```

## Supported Operations

### Resource Types

| Resource Type | CREATE | UPDATE | DELETE | Description |
|---------------|--------|--------|--------|-------------|
| `Things` | ✅ | ✅ | ✅ | Physical or virtual IoT devices |
| `Locations` | ✅ | ✅ | ✅ | Geospatial locations (GeoJSON) |
| `Sensors` | ✅ | ✅ | ✅ | Measurement instruments |
| `ObservedProperties` | ✅ | ✅ | ✅ | Phenomena being observed |
| `Datastreams` | ✅ | ✅ | ✅ | Collections of observations |
| `Projects` | ✅ | ✅ | ✅ | FROST Projects extension |

### Subscribed Topics (18 Topics)

The adapter subscribes to all SensorThings entity lifecycle events:

**Thing Events (3):**
- `de.civitascore.data.thing.created`
- `de.civitascore.data.thing.updated`
- `de.civitascore.data.thing.deleted`

**Location Events (3):**
- `de.civitascore.data.location.created`
- `de.civitascore.data.location.updated`
- `de.civitascore.data.location.deleted`

**Sensor Events (3):**
- `de.civitascore.data.sensor.created`
- `de.civitascore.data.sensor.updated`
- `de.civitascore.data.sensor.deleted`

**ObservedProperty Events (3):**
- `de.civitascore.data.observedproperty.created`
- `de.civitascore.data.observedproperty.updated`
- `de.civitascore.data.observedproperty.deleted`

**Datastream Events (3):**
- `de.civitascore.data.datastream.created`
- `de.civitascore.data.datastream.updated`
- `de.civitascore.data.datastream.deleted`

**Project Events (3):**
- `de.civitascore.data.project.created`
- `de.civitascore.data.project.updated`
- `de.civitascore.data.project.deleted`

## Configuration

### Required Properties

```properties
# FROST-Server URL
frost.url=http://localhost:8080/FROST-Server/v1.1

# API key for authentication (required)
frost.api.key=your-frost-api-key

# HTTP header name for the API key (optional, defaults to X-API-Key)
frost.api.key.header=X-API-Key

# Topics to subscribe to (comma-separated)
frost.topics=de.civitascore.data.thing.created,de.civitascore.data.thing.updated,de.civitascore.data.thing.deleted,de.civitascore.data.location.created,de.civitascore.data.location.updated,de.civitascore.data.location.deleted,de.civitascore.data.sensor.created,de.civitascore.data.sensor.updated,de.civitascore.data.sensor.deleted,de.civitascore.data.observedproperty.created,de.civitascore.data.observedproperty.updated,de.civitascore.data.observedproperty.deleted,de.civitascore.data.datastream.created,de.civitascore.data.datastream.updated,de.civitascore.data.datastream.deleted
```

### Environment Variables

All properties can be overridden with environment variables:

```bash
FROST_URL=http://frost-server:8080/FROST-Server/v1.1
FROST_API_KEY=your-api-key
FROST_API_KEY_HEADER=X-API-Key
FROST_TOPICS=de.civitascore.data.thing.created,de.civitascore.data.thing.updated
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  config-adapter:
    image: config-adapter:latest
    environment:
      ADAPTERS: frost
      EVENTHANDLER_NAME: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      FROST_URL: http://frost-server:8080/FROST-Server/v1.1
      FROST_API_KEY: ${FROST_API_KEY}
      FROST_TOPICS: de.civitascore.data.thing.created,de.civitascore.data.thing.updated,de.civitascore.data.thing.deleted
    depends_on:
      - kafka
      - frost-server
```

## Event Format

### Input Event (CloudEvent)

#### Thing Create Example

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.thing.created",
  "source": "civitas.iot.provisioning",
  "id": "event-123",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-456",
      "timestamp": "2026-01-15T10:00:00Z",
      "source": "iot.service",
      "correlationId": "corr-789",
      "configVersion": "1.0",
      "resultTopic": "de.civitascore.data.processing.result"
    },
    "payload": {
      "targetComponent": "frost",
      "targetResource": "Things",
      "operation": "CREATE",
      "config": {
        "value": {
          "name": "Temperature Sensor Unit 1",
          "description": "A temperature sensor in building A",
          "properties": {
            "serialNumber": "TMP-001",
            "manufacturer": "SensorCorp"
          }
        }
      }
    }
  }
}
```

#### Location Create Example (GeoJSON)

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.location.created",
  "source": "civitas.iot.provisioning",
  "id": "event-456",
  "data": {
    "metadata": {
      "messageId": "msg-789",
      "correlationId": "corr-012",
      "resultTopic": "de.civitascore.data.processing.result"
    },
    "payload": {
      "targetComponent": "frost",
      "targetResource": "Locations",
      "operation": "CREATE",
      "config": {
        "value": {
          "name": "Building A, Room 101",
          "description": "Main entrance sensor location",
          "encodingType": "application/geo+json",
          "location": {
            "type": "Point",
            "coordinates": [8.4037, 49.0069]
          }
        }
      }
    }
  }
}
```

#### Project-Scoped Thing Create Example

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.thing.created",
  "source": "civitas.iot.provisioning",
  "id": "event-789",
  "data": {
    "metadata": {
      "messageId": "msg-012",
      "correlationId": "corr-345",
      "resultTopic": "de.civitascore.data.processing.result"
    },
    "payload": {
      "targetComponent": "frost",
      "targetResource": "Projects/42/Things",
      "operation": "CREATE",
      "config": {
        "value": {
          "name": "Project-Scoped Sensor",
          "description": "A sensor within project 42"
        }
      }
    }
  }
}
```

#### Datastream Create Example

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.datastream.created",
  "source": "civitas.iot.provisioning",
  "id": "event-012",
  "data": {
    "metadata": {
      "messageId": "msg-345",
      "correlationId": "corr-678",
      "resultTopic": "de.civitascore.data.processing.result"
    },
    "payload": {
      "targetComponent": "frost",
      "targetResource": "Datastreams",
      "operation": "CREATE",
      "config": {
        "value": {
          "name": "Temperature Readings",
          "description": "Datastream for temperature observations",
          "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
          "unitOfMeasurement": {
            "name": "Degree Celsius",
            "symbol": "°C",
            "definition": "http://www.qudt.org/qudt/owl/1.0.0/unit/Instances.html#DegreeCelsius"
          },
          "Thing": {"@iot.id": 1},
          "Sensor": {"@iot.id": 1},
          "ObservedProperty": {"@iot.id": 1}
        }
      }
    }
  }
}
```

### Output Event (Result)

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.processing.result",
  "source": "civitas.config-adapter.frost",
  "id": "result-123",
  "datacontenttype": "application/json",
  "correlationid": "corr-789",
  "originalmessageid": "msg-456",
  "status": "SUCCESS",
  "operation": "CREATE",
  "targetresource": "Things",
  "resourceid": "123",
  "data": {
    "correlationId": "corr-789",
    "originalMessageId": "msg-456",
    "status": "SUCCESS",
    "message": "FROST THING created successfully",
    "resourceId": "123",
    "operation": "CREATE",
    "targetResource": "Things",
    "timestamp": "2026-01-15T10:00:01Z",
    "source": "civitas.config-adapter.frost"
  }
}
```

## Error Handling

### HTTP Status Code Mapping

The adapter categorizes errors based on HTTP response status:

| HTTP Status | Exception Type | Behavior |
|-------------|----------------|----------|
| 5xx (Server Error) | `RetryableAdapterException` | Blocking retry with exponential backoff |
| 4xx (Client Error) | `FatalAdapterException` | Send to DLQ |
| Network Error | `RetryableAdapterException` | Blocking retry with exponential backoff |

### FROST-Specific Error Codes

| Code | Name | Description |
|------|------|-------------|
| 3201 | `FROST_ENTITY_ERROR` | Entity operation failed (HTTP 4xx) |
| 1001 | `NETWORK_ERROR` | Network connectivity issue |
| 1002 | `SERVICE_UNAVAILABLE` | FROST-Server unavailable (HTTP 5xx) |

### Error Response Example

```json
{
  "correlationId": "corr-789",
  "originalMessageId": "msg-456",
  "status": "FAILURE",
  "errorCode": "FROST_ENTITY_ERROR(3201)",
  "message": "HTTP 400: {\"message\":\"Required field 'name' is missing\"}",
  "operation": "CREATE",
  "targetResource": "Things",
  "timestamp": "2026-01-15T10:00:01Z"
}
```

## Advanced Features

### Project-Scoped Entity Creation

The FROST adapter supports the FROST Projects extension for multi-tenant IoT deployments. Entities can be created within a specific project scope:

```
targetResource: "Projects/{projectId}/Things"
targetResource: "Projects/{projectId}/Locations"
targetResource: "Projects/{projectId}/Sensors"
```

The adapter parses the path and creates entities within the specified project context.

### Entity Relationships

SensorThings API entities can reference each other. When creating Datastreams, you can link to existing entities:

```json
{
  "name": "Temperature Stream",
  "Thing": {"@iot.id": 1},
  "Sensor": {"@iot.id": 2},
  "ObservedProperty": {"@iot.id": 3}
}
```

## Testing

### Unit Tests

```bash
mvn test -pl config-adapter-frost
```

Tests cover:
- Successful create/update/delete operations for all entity types
- HTTP error handling (4xx, 5xx)
- Network error handling
- Project-scoped operations
- Result event publishing

### Integration Tests

Integration tests use Testcontainers with real FROST-Server and PostGIS instances:

```bash
mvn verify -pl config-adapter-frost
```

Test scenarios:
- Create/update/delete Things
- Create/update/delete Locations with GeoJSON
- Create/update/delete Sensors
- FROST Projects CRUD operations
- Project-scoped entity creation
- Error handling for invalid configurations

## Troubleshooting

### Common Issues

#### 1. Connection Refused

```
Network error during FROST entity creation: Connection refused
```

**Solution:** Verify FROST-Server is running and `frost.url` is correct.

#### 2. Unauthorized (401)

```
FROST client error during entity creation: HTTP 401
```

**Solution:** Check `frost.api.key` is correct and the API key header matches FROST-Server configuration.

#### 3. Bad Request (400)

```
FROST client error during entity creation: HTTP 400
```

**Solution:** Validate entity configuration against OGC SensorThings API specification. Ensure required fields are present.

#### 4. Not Found (404)

```
FROST client error during entity update: HTTP 404
```

**Solution:** For UPDATE/DELETE operations, ensure the entity exists. Check the entity ID in the targetResource path.

## Resources

- [FROST-Server Documentation](https://fraunhoferiosb.github.io/FROST-Server/)
- [OGC SensorThings API](https://www.ogc.org/standards/sensorthings)
- [SensorThings API Part 1: Sensing](https://docs.ogc.org/is/18-088/18-088.html)
- [CloudEvents Specification](https://cloudevents.io/)

## License

European Union Public License (EU-PL) 1.2
