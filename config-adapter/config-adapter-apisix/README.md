# APISIX Config Adapter

Production-ready adapter for managing [Apache APISIX](https://apisix.apache.org/) API Gateway configuration through CloudEvents.

## Overview

The APISIX adapter integrates with Apache APISIX API Gateway's Admin API to manage upstream backend services. It consumes CloudEvents from Kafka and translates them into APISIX Admin API calls, enabling automated configuration management for your API Gateway infrastructure.

**Key Features:**
- ✅ JAX-RS Client for clean, fluent REST API calls
- ✅ Full CRUD operations for APISIX upstreams
- ✅ Asynchronous result publishing via CloudEvents
- ✅ Comprehensive error handling and reporting
- ✅ Production-ready with integration tests
- ✅ Testcontainers-based testing with real APISIX instances

## Architecture

```
┌─────────────────┐
│  Kafka Topics   │
│  - backend.*    │
└────────┬────────┘
         │ CloudEvents
         ↓
┌────────────────────────┐
│   ApisixAdapter        │
│  - JAX-RS Client       │
│  - Event Processing    │
│  - Error Handling      │
└────────┬───────────────┘
         │ REST API (9180)
         ↓
┌────────────────────────┐
│   APISIX Admin API     │
│  - Upstream Management │
│  - Configuration       │
└────────────────────────┘
```

## Technology Stack

- **JAX-RS Client API** (Jakarta 3.1.0) - REST client interface
- **Jersey Client** (3.1.5) - JAX-RS implementation
- **Jackson** - JSON processing (integrated with Jersey)
- **SLF4J** - Logging
- **CloudEvents** - Event format specification

## Supported Operations

### Upstream Management

| Operation | HTTP Method | Endpoint | Description |
|-----------|-------------|----------|-------------|
| CREATE | POST | `/apisix/admin/upstreams` | Create new upstream |
| UPDATE | PUT | `/apisix/admin/upstreams/{id}` | Update existing upstream |
| DELETE | DELETE | `/apisix/admin/upstreams/{id}` | Delete upstream |

### Subscribed Topics

The adapter subscribes to backend lifecycle events:

- `core.civitas.api.backend.created` - New backend services
- `core.civitas.api.backend.updated` - Backend updates
- `core.civitas.api.backend.deleted` - Backend removal

## Configuration

### Required Properties

```properties
# APISIX Admin API URL
apisix.admin.url=http://localhost:9180

# APISIX Admin API Key (for authentication)
apisix.admin.key=edd1c9f034335f136f87ad84b625c8f1

# Topics to subscribe to
apisix.topics=core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted
```

### Environment Variables

All properties can be overridden with environment variables:

```bash
APISIX_ADMIN_URL=http://apisix:9180
APISIX_ADMIN_KEY=your-api-key
APISIX_TOPICS=core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  config-adapter:
    image: config-adapter:latest
    environment:
      ADAPTERS: apisix
      EVENTHANDLER_NAME: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      APISIX_ADMIN_URL: http://apisix:9180
      APISIX_ADMIN_KEY: ${APISIX_API_KEY}
      APISIX_TOPICS: core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted
    depends_on:
      - kafka
      - apisix
```

## Event Format

### Input Event (CloudEvent)

```json
{
  "specversion": "1.0",
  "type": "core.civitas.api.backend.created",
  "source": "civitas.api.provisioning",
  "id": "event-123",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-456",
      "timestamp": "2025-01-15T10:00:00Z",
      "source": "api.service",
      "correlationId": "corr-789",
      "configVersion": "1.0",
      "resultTopic": "api.results"
    },
    "payload": {
      "targetComponent": "apisix",
      "targetResource": "upstreams/my-backend",
      "operation": "CREATE",
      "config": {
        "path": "upstreams/my-backend",
        "value": {
          "type": "roundrobin",
          "nodes": {
            "backend1.example.com:8080": 1,
            "backend2.example.com:8080": 1,
            "backend3.example.com:8080": 1
          },
          "timeout": {
            "connect": 6,
            "send": 6,
            "read": 6
          }
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
  "type": "config.result",
  "source": "civitas.config-adapter.apisix",
  "id": "result-123",
  "datacontenttype": "application/json",
  "data": {
    "correlationId": "corr-789",
    "originalMessageId": "msg-456",
    "status": "SUCCESS",
    "message": "APISIX upstream updated successfully",
    "resourceId": "my-backend",
    "operation": "UPDATE",
    "targetResource": "upstreams/my-backend",
    "timestamp": "2025-01-15T10:00:01Z"
  }
}
```

## APISIX Admin API Integration

### Authentication

All requests include the `X-API-KEY` header for authentication:

```java
client.target(adminApiUrl)
    .path("/apisix/admin/upstreams")
    .request(MediaType.APPLICATION_JSON)
    .header("X-API-KEY", adminApiKey)
    .post(Entity.json(upstreamConfig));
```

### Upstream Configuration

APISIX upstreams support various load balancing algorithms and health check configurations:

#### Load Balancing Algorithms

- `roundrobin` - Round-robin (default)
- `chash` - Consistent hashing
- `ewma` - Exponentially weighted moving average
- `least_conn` - Least connections

#### Example: Weighted Round-Robin

```json
{
  "type": "roundrobin",
  "nodes": {
    "backend1:8080": 1,
    "backend2:8080": 2,
    "backend3:8080": 1
  }
}
```

#### Example: With Health Checks

```json
{
  "type": "roundrobin",
  "nodes": {
    "backend1:8080": 1,
    "backend2:8080": 1
  },
  "checks": {
    "active": {
      "type": "http",
      "http_path": "/health",
      "healthy": {
        "interval": 5,
        "successes": 2
      },
      "unhealthy": {
        "interval": 5,
        "http_failures": 3
      }
    }
  }
}
```

#### Example: With TLS

```json
{
  "type": "roundrobin",
  "scheme": "https",
  "nodes": {
    "secure-backend:8443": 1
  },
  "tls": {
    "client_cert": "...",
    "client_key": "..."
  }
}
```

## Error Handling

### Error Codes

| Error Code | Description | Action |
|------------|-------------|--------|
| `UPSTREAM_CREATE_FAILED` | Failed to create upstream | Check configuration and APISIX logs |
| `UPSTREAM_UPDATE_FAILED` | Failed to update upstream | Verify upstream exists and config is valid |
| `UPSTREAM_DELETE_FAILED` | Failed to delete upstream | Check if upstream is in use by routes |
| `UNKNOWN_RESOURCE_TYPE` | Unsupported resource type | Currently only `upstreams` is supported |
| `PROCESSING_ERROR` | General processing error | Check event format and APISIX availability |

### Error Response Example

```json
{
  "status": "FAILURE",
  "errorCode": "UPSTREAM_CREATE_FAILED",
  "errorMessage": "Failed to create APISIX upstream. Status: 400, Body: {\"error_msg\":\"invalid configuration\"}"
}
```

### Common Issues

#### 1. Connection Refused

```
Failed to create APISIX upstream
jakarta.ws.rs.ProcessingException: Connection refused
```

**Solution:** Verify APISIX is running and `apisix.admin.url` is correct.

#### 2. Unauthorized (401)

```
Failed to create APISIX upstream. Status: 401
```

**Solution:** Check `apisix.admin.key` matches APISIX configuration.

#### 3. Invalid Configuration (400)

```
Status: 400, Body: {"error_msg":"invalid configuration: value should match only one schema"}
```

**Solution:** Validate upstream configuration against APISIX schema. Ensure required fields are present.

#### 4. Resource Not Found (404)

```
Status: 404, Body: {"error_msg":"not found"}
```

**Solution:** For UPDATE/DELETE operations, ensure the upstream exists first.

## Testing

### Unit Tests

Run unit tests with mocked dependencies:

```bash
mvn test -Dtest=ApisixAdapterTest
```

Tests cover:
- Successful create/update/delete operations
- HTTP error handling (4xx, 5xx)
- Network exceptions
- Result event publishing
- Null safety

### Integration Tests

Run integration tests with real APISIX and etcd containers:

```bash
mvn test -Dtest=ApisixAdapterIntegrationTest
```

Integration tests use:
- **Testcontainers** for Docker management
- **etcd v3.6.6** as APISIX configuration store
- **APISIX 3.14.0-debian** for API Gateway
- **Awaitility** for async assertion

Test scenarios:
- ✅ Create upstream successfully
- ✅ Update upstream with new nodes
- ✅ Delete upstream
- ✅ Handle invalid configuration gracefully

### Test Coverage

```bash
mvn verify
```

Coverage reports are generated in `target/site/jacoco/index.html`.

## Implementation Details

### JAX-RS Client Benefits

The adapter uses JAX-RS Client API for clean, maintainable code:

**Before (java.net.http.HttpClient):**
```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .header("Content-Type", "application/json")
    .header("X-API-KEY", adminApiKey)
    .PUT(HttpRequest.BodyPublishers.ofString(json))
    .timeout(Duration.ofSeconds(30))
    .build();
HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
```

**After (JAX-RS Client):**
```java
Response response = client.target(adminApiUrl)
    .path("/apisix/admin/upstreams/{id}")
    .resolveTemplate("id", upstreamId)
    .request(MediaType.APPLICATION_JSON)
    .header("X-API-KEY", adminApiKey)
    .put(Entity.json(configValue));
```

**Benefits:**
- Fluent, readable API
- Built-in JSON marshalling with Jackson
- Path template support
- Type-safe responses
- Better for complex operations (routes API coming soon)

### Resource Parsing

The adapter parses `targetResource` to extract resource type and ID:

- `upstreams` → CREATE (APISIX generates ID)
- `upstreams/my-backend` → UPDATE or DELETE on specific upstream

```java
private record ResourceInfo(String type, String id) {}

private ResourceInfo parseTargetResource(String targetResource) {
    // Parses: "upstreams/my-backend-id"
    //      → ResourceInfo("upstream", "my-backend-id")
}
```

### Lifecycle Management

```java
public class ApisixAdapter extends AbstractConfigAdapter {

    @Override
    public void initialize(AdapterConfig config) {
        this.adminApiUrl = getAdapterProperty("admin.url", "http://localhost:9180");
        this.adminApiKey = getAdapterProperty("admin.key", "...");
        this.client = createClient();
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();  // Release connection resources
        }
    }
}
```

## Future Enhancements

### Routes API (Planned)

Support for APISIX routes configuration:

```json
{
  "targetResource": "routes/api-route",
  "operation": "CREATE",
  "config": {
    "uri": "/api/*",
    "upstream_id": "my-backend",
    "plugins": {
      "rate-limit": {
        "count": 100,
        "time_window": 60
      }
    }
  }
}
```

### SSL/TLS Certificates (Planned)

Manage SSL certificates and SNI configuration.

### Plugin Configuration (Planned)

Configure APISIX plugins via CloudEvents.

## Performance Considerations

### Connection Pooling

JAX-RS Client automatically manages connection pooling:

```java
Client client = ClientBuilder.newBuilder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();
```

### Timeouts

- **Connect timeout:** 10 seconds
- **Read timeout:** 30 seconds

Adjust in `createClient()` if needed for your environment.

### Concurrency

The adapter is thread-safe and can process multiple events concurrently. Each event is processed independently.

## Troubleshooting

### Enable Debug Logging

```properties
# application.properties
logging.level.com.civitas.configadapter.apisix=DEBUG
```

Or via environment:
```bash
LOGGING_LEVEL_COM_CIVITAS_CONFIGADAPTER_APISIX=DEBUG
```

### Verify APISIX Connectivity

```bash
curl -H "X-API-KEY: edd1c9f034335f136f87ad84b625c8f1" \
     http://localhost:9180/apisix/admin/upstreams
```

### Check Event Format

Ensure CloudEvents match the expected schema (see Event Format section).

### Monitor Result Topic

Subscribe to the result topic to see success/failure responses:

```bash
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic api.results --from-beginning
```

## Resources

- [Apache APISIX Documentation](https://apisix.apache.org/docs/)
- [APISIX Admin API Reference](https://apisix.apache.org/docs/apisix/admin-api/)
- [JAX-RS Client API](https://jakarta.ee/specifications/restful-ws/3.1/)
- [CloudEvents Specification](https://cloudevents.io/)

## Contributing

When adding new features:

1. **Add unit tests** in `ApisixAdapterTest`
2. **Add integration tests** in `ApisixAdapterIntegrationTest`
3. **Update this README** with new configuration options
4. **Follow existing patterns** for consistency
5. **Test with real APISIX** using integration tests

## License

Eclipse Public License 2.0 (EPL-2.0)
