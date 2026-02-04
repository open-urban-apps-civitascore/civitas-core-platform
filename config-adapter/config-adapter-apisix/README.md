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
│  - route.*      │
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
│  - Route Management    │
│  - Plugin Config       │
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

### Route Management

| Operation | HTTP Method | Endpoint | Description |
|-----------|-------------|----------|-------------|
| CREATE | POST | `/apisix/admin/routes` | Create new route |
| UPDATE | PUT | `/apisix/admin/routes/{id}` | Update existing route |
| DELETE | DELETE | `/apisix/admin/routes/{id}` | Delete route |

### Subscribed Topics

The adapter subscribes to backend and route lifecycle events:

**Backend Topics:**
- `core.civitas.api.backend.created` - New backend services
- `core.civitas.api.backend.updated` - Backend updates
- `core.civitas.api.backend.deleted` - Backend removal

**Route Topics:**
- `core.civitas.api.route.created` - New routes
- `core.civitas.api.route.updated` - Route updates
- `core.civitas.api.route.deleted` - Route removal

## Configuration

### Required Properties

```properties
# APISIX Admin API URL
apisix.admin.url=http://localhost:9180

# APISIX Admin API Key (for authentication)
apisix.admin.key=edd1c9f034335f136f87ad84b625c8f1

# Topics to subscribe to (backend and route events)
apisix.topics=core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted,core.civitas.api.route.created,core.civitas.api.route.updated,core.civitas.api.route.deleted
```

### Environment Variables

All properties can be overridden with environment variables:

```bash
APISIX_ADMIN_URL=http://apisix:9180
APISIX_ADMIN_KEY=your-api-key
APISIX_TOPICS=core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted,core.civitas.api.route.created,core.civitas.api.route.updated,core.civitas.api.route.deleted
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
      APISIX_TOPICS: core.civitas.api.backend.created,core.civitas.api.backend.updated,core.civitas.api.backend.deleted,core.civitas.api.route.created,core.civitas.api.route.updated,core.civitas.api.route.deleted
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

| Algorithm | Description | Use Case |
|-----------|-------------|----------|
| `roundrobin` | Distributes requests sequentially to each backend in turn (default) | General purpose, evenly distributed backends |
| `chash` | Consistent hashing based on specified key (e.g., client IP, header) | Session affinity, cache optimization |
| `ewma` | Exponentially Weighted Moving Average - routes to lowest latency backend | Latency-sensitive applications |
| `least_conn` | Routes to backend with fewest active connections | Long-lived connections, varying request durations |

**Algorithm Configuration Examples:**

```json
// Round-robin with weights
{
  "type": "roundrobin",
  "nodes": {
    "backend1:8080": 3,
    "backend2:8080": 2,
    "backend3:8080": 1
  }
}

// Consistent hashing by client IP
{
  "type": "chash",
  "hash_on": "vars",
  "key": "remote_addr",
  "nodes": {
    "backend1:8080": 1,
    "backend2:8080": 1
  }
}

// EWMA for latency optimization
{
  "type": "ewma",
  "nodes": {
    "backend1:8080": 1,
    "backend2:8080": 1
  }
}

// Least connections
{
  "type": "least_conn",
  "nodes": {
    "backend1:8080": 1,
    "backend2:8080": 1
  }
}
```

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
| `ROUTE_CREATE_FAILED` | Failed to create route | Verify uri and upstream_id are valid |
| `ROUTE_UPDATE_FAILED` | Failed to update route | Verify route exists and config is valid |
| `ROUTE_DELETE_FAILED` | Failed to delete route | Check route ID exists |
| `UNKNOWN_RESOURCE_TYPE` | Unsupported resource type | Only `upstreams` and `routes` are supported |
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
- ✅ Create route with plugins
- ✅ Update route configuration
- ✅ Delete route
- ✅ Create route with serverless-post-function (log phase)
- ✅ Create route with serverless-post-function (header_filter phase)
- ✅ Update route to add serverless-post-function plugin
- ✅ Create route with multiple Lua functions
- ✅ Combine serverless-post-function with other plugins
- ✅ Create route with serverless-pre-function (rewrite phase)
- ✅ Create route with serverless-pre-function (access phase)
- ✅ Update route to add serverless-pre-function plugin
- ✅ Create route with multiple serverless-pre-function Lua functions
- ✅ Combine serverless-pre-function with serverless-post-function

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

## Route Configuration

Routes define how requests are matched and forwarded to upstreams. The adapter supports full CRUD operations for routes with plugin configuration.

### Route Event Format

```json
{
  "specversion": "1.0",
  "type": "core.civitas.api.route.created",
  "source": "civitas.api.provisioning",
  "id": "event-route-123",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-route-456",
      "timestamp": "2025-01-22T10:00:00Z",
      "source": "api.service",
      "correlationId": "corr-route-789",
      "configVersion": "1.0",
      "resultTopic": "api.results"
    },
    "payload": {
      "targetComponent": "apisix",
      "targetResource": "routes/my-api-route",
      "operation": "CREATE",
      "config": {
        "path": "routes/my-api-route",
        "value": {
          "uri": "/api/v1/*",
          "methods": ["GET", "POST", "PUT", "DELETE"],
          "upstream_id": "my-backend",
          "plugins": {
            "prometheus": {},
            "proxy-rewrite": {
              "uri": "/rewritten"
            }
          }
        }
      }
    }
  }
}
```

### Route Configuration Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `uri` | string | Yes | URI path pattern (supports wildcards with `*`) |
| `methods` | array | No | HTTP methods to match (e.g., `["GET", "POST"]`) |
| `upstream_id` | string | Yes* | Reference to existing upstream |
| `upstream` | object | Yes* | Inline upstream definition |
| `plugins` | object | No | Plugin configurations |
| `host` | string | No | Match specific host header |
| `hosts` | array | No | Match multiple hosts |
| `priority` | integer | No | Route priority (higher = more priority) |

*Either `upstream_id` or `upstream` must be provided.

## serverless-post-function Plugin

The `serverless-post-function` plugin allows executing custom Lua code **after** the request has been processed. This is useful for:

- **Response manipulation** - Add/modify headers after upstream response
- **Custom logging** - Log metrics or data after request completion
- **Post-processing logic** - Execute cleanup or notification tasks

### Available Phases

| Phase | Description | Use Case |
|-------|-------------|----------|
| `rewrite` | During request rewriting | Modify request before proxy |
| `access` | After access phase | Post-authentication logic |
| `header_filter` | After receiving response headers | Modify response headers |
| `body_filter` | After receiving response body | Modify response body |
| `log` | At the end of request processing | Logging, metrics, cleanup |

### Plugin Configuration

```json
{
  "serverless-post-function": {
    "phase": "log",
    "functions": [
      "return function(conf, ctx) ngx.log(ngx.INFO, 'Request completed for: ' .. ngx.var.uri) end"
    ]
  }
}
```

### Configuration Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `phase` | string | No | Execution phase (default: `access`) |
| `functions` | array | Yes | Array of Lua function strings |

### Example: Add Custom Response Header

```json
{
  "plugins": {
    "serverless-post-function": {
      "phase": "header_filter",
      "functions": [
        "return function(conf, ctx) ngx.header['X-Processed-By'] = 'civitas-gateway' end"
      ]
    }
  }
}
```

### Example: Custom Logging

```json
{
  "plugins": {
    "serverless-post-function": {
      "phase": "log",
      "functions": [
        "return function(conf, ctx) ngx.log(ngx.INFO, 'Method: ' .. ngx.var.request_method .. ', URI: ' .. ngx.var.uri .. ', Status: ' .. ngx.var.status) end"
      ]
    }
  }
}
```

### Example: Multiple Functions

```json
{
  "plugins": {
    "serverless-post-function": {
      "phase": "log",
      "functions": [
        "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 1: Request logged') end",
        "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 2: Metrics sent') end"
      ]
    }
  }
}
```

### Combined with Other Plugins

The `serverless-post-function` plugin can be combined with other plugins:

```json
{
  "plugins": {
    "prometheus": {},
    "response-rewrite": {
      "headers": {
        "set": {
          "X-Upstream-Response-Time": "$upstream_response_time"
        }
      }
    },
    "serverless-post-function": {
      "phase": "log",
      "functions": [
        "return function(conf, ctx) ngx.log(ngx.INFO, 'Request processed: ' .. ngx.var.uri) end"
      ]
    }
  }
}
```

### Lua Function Syntax

Lua functions must follow this format:
```lua
return function(conf, ctx)
    -- Your code here
    -- conf: plugin configuration
    -- ctx: request context
end
```

**Available ngx variables:**
- `ngx.var.uri` - Request URI
- `ngx.var.request_method` - HTTP method
- `ngx.var.status` - Response status code
- `ngx.var.remote_addr` - Client IP
- `ngx.header['Header-Name']` - Response headers (in header_filter phase)

**Important Notes:**
- APISIX validates Lua syntax - invalid code returns HTTP 400
- Functions execute in order when multiple are provided
- Use `ngx.log(ngx.INFO, ...)` for logging (visible in APISIX error.log)

## serverless-pre-function Plugin

The `serverless-pre-function` plugin allows executing custom Lua code **before** the request is proxied to the upstream. This is useful for:

- **Request modification** - Add/modify headers before proxying
- **Early validation** - Check request properties before processing
- **Request enrichment** - Inject correlation IDs, timestamps, etc.

### Available Phases

| Phase | Description | Use Case |
|-------|-------------|----------|
| `rewrite` | During request rewriting (default) | Modify request before proxy |
| `access` | After access phase | Post-authentication logic |

### Plugin Configuration

```json
{
  "serverless-pre-function": {
    "phase": "rewrite",
    "functions": [
      "return function(conf, ctx) ngx.req.set_header('X-Request-ID', ngx.var.request_id) end"
    ]
  }
}
```

### Configuration Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `phase` | string | No | Execution phase (default: `rewrite`) |
| `functions` | array | Yes | Array of Lua function strings |

### Example: Add Request Headers

```json
{
  "plugins": {
    "serverless-pre-function": {
      "phase": "rewrite",
      "functions": [
        "return function(conf, ctx) ngx.req.set_header('X-Request-ID', ngx.var.request_id) end"
      ]
    }
  }
}
```

### Example: Request Timestamp Injection

```json
{
  "plugins": {
    "serverless-pre-function": {
      "phase": "rewrite",
      "functions": [
        "return function(conf, ctx) ngx.req.set_header('X-Request-Start', tostring(ngx.now())) end"
      ]
    }
  }
}
```

### Example: Combining Pre and Post Functions

Use `serverless-pre-function` to modify requests before proxy and `serverless-post-function` to process responses:

```json
{
  "plugins": {
    "serverless-pre-function": {
      "phase": "rewrite",
      "functions": [
        "return function(conf, ctx) ngx.req.set_header('X-Request-Start', tostring(ngx.now())) end"
      ]
    },
    "serverless-post-function": {
      "phase": "log",
      "functions": [
        "return function(conf, ctx) ngx.log(ngx.INFO, 'Request completed: ' .. ngx.var.uri) end"
      ]
    }
  }
}
```

### Difference from serverless-post-function

| Aspect | serverless-pre-function | serverless-post-function |
|--------|------------------------|-------------------------|
| **Execution Time** | Before proxy to upstream | After upstream response |
| **Primary Use** | Request modification | Response processing |
| **Common Phases** | `rewrite`, `access` | `header_filter`, `body_filter`, `log` |
| **Can Modify** | Request headers, URI | Response headers, body |

## Future Enhancements

### SSL/TLS Certificates (Planned)

Manage SSL certificates and SNI configuration.

### Services API (Planned)

Support for APISIX services for shared route configurations.

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
