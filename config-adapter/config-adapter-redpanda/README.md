# RedPanda Connect Config Adapter

Production-ready adapter for managing [RedPanda Connect](https://www.redpanda.com/connect) data pipelines through CloudEvents and Saga orchestration.

## Overview

The RedPanda Connect adapter integrates with the RedPanda Connect Streams API to manage data pipelines. It supports two integration paths:

1. **ConfigAdapter** (CloudEvents) — Single pipeline lifecycle events from Kafka
2. **SagaCommandHandler** (Saga Orchestrator) — Multi-pipeline operations in a dataset context with compensation support

Pipeline definitions are converted from JSON to YAML before being sent to the Streams API. Encrypted credentials (`ENC(...)` values) are automatically decrypted at send time.

**Key Features:**
- Full CRUD operations for data pipelines via the Streams API (port 4195)
- Saga orchestration with forward execution and compensation (rollback)
- Automatic JSON-to-YAML conversion for pipeline definitions
- AES-256-GCM credential decryption for sensitive pipeline configuration
- Extensible pipeline model (MQTT, SQL, HTTP, Bloblang processors)
- Asynchronous result publishing via CloudEvents
- Comprehensive error handling with HTTP status-based categorization
- Integration tests with Testcontainers against real RedPanda Connect instances

## Architecture

```
┌───────────────────────────────────┐
│         Kafka Topics              │
│  - pipeline.created/updated/del.  │
│  - dataset.redpanda.execute       │
│  - dataset.redpanda.compensate    │
└──────────┬────────────┬───────────┘
           │            │
   CloudEvents    SagaCommands
           │            │
           ↓            ↓
┌──────────────┐ ┌──────────────────┐
│ RedpandaAdap.│ │ RedpandaSagaHand.│
│ (ConfigAdapt)│ │ (SagaCommandHand)│
└──────┬───────┘ └────────┬─────────┘
       │                  │
       └────────┬─────────┘
                ↓
┌────────────────────────────┐
│   RedpandaConnectClient    │
│  - JSON → YAML conversion  │
│  - ENC(...) decryption     │
│  - JAX-RS HTTP Client      │
└────────────┬───────────────┘
             │ REST API (4195)
             ↓
┌────────────────────────────┐
│   RedPanda Connect         │
│  - Streams API             │
│  - POST/PUT/DELETE /streams│
│  - Pipeline Management     │
└────────────────────────────┘
```

## Supported Operations

### ConfigAdapter Path (CloudEvents)

| Operation | HTTP Method | Endpoint | Description | Idempotent |
|-----------|-------------|----------|-------------|-----------|
| `CREATE` | `POST` | `/streams/{id}` | Create a new pipeline | Yes — HTTP 409 treated as success |
| `UPDATE` | `PUT` | `/streams/{id}` | Update an existing pipeline (upsert if absent) | Yes — HTTP 404 triggers CREATE |
| `DELETE` | `DELETE` | `/streams/{id}` | Delete a pipeline | Yes — HTTP 404 treated as success |

### Saga Orchestrator Path

| Saga Operation | Description | Compensation |
|----------------|-------------|--------------|
| `DEPLOY_PIPELINES` | Create all pipelines for a new dataset | `DELETE_PIPELINES` |
| `UPDATE_PIPELINES` | Update pipelines (ADD/UPDATE/DELETE per pipeline) | `RESTORE_PIPELINES` |
| `DELETE_PIPELINES` | Delete all pipelines for a dataset | — |
| `RESTORE_PIPELINES` | Restore pipelines to previous state | — |

All saga steps are **conditional** — they are skipped if the dataset has no data pipelines.

### Subscribed Topics (3 Topics)

**Pipeline Events:**
- `de.civitascore.data.pipeline.created`
- `de.civitascore.data.pipeline.updated`
- `de.civitascore.data.pipeline.deleted`

**Saga Topics:**
- `de.civitascore.dataset.redpanda.execute` — Forward execution commands
- `de.civitascore.dataset.redpanda.compensate` — Compensation commands

## Configuration

### Required Properties

```properties
# RedPanda Connect Streams API URL
redpanda.url=http://localhost:4195

# Topics to subscribe to (comma-separated)
redpanda.topics=de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted
```

### Environment Variables

All properties can be overridden with environment variables:

```bash
REDPANDA_URL=http://redpanda-connect:4195
REDPANDA_TOPICS=de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted

# Credential decryption (optional, for ENC(...) values in pipeline configs)
# Generate with: openssl rand -hex 32
CIVITAS_MASTER_KEY=<256-bit hex-encoded key>
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  config-adapter:
    image: config-adapter:latest
    environment:
      ADAPTERS: redpanda
      EVENTHANDLER_NAME: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      REDPANDA_URL: http://redpanda-connect:4195
      REDPANDA_TOPICS: de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted
      CIVITAS_MASTER_KEY: ${CIVITAS_MASTER_KEY}
    depends_on:
      - kafka
      - redpanda-connect

  redpanda-connect:
    image: docker.redpanda.com/redpandadata/connect
    ports:
      - "4195:4195"
```

## Event Format

### Input Event (CloudEvent) — Pipeline Create

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.pipeline.created",
  "source": "civitas.data.provisioning",
  "id": "event-123",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-456",
      "timestamp": "2026-01-15T10:00:00Z",
      "source": "data.service",
      "correlationId": "corr-789",
      "configVersion": "1.0",
      "resultTopic": "de.civitascore.data.pipeline.processing.result"
    },
    "payload": {
      "targetComponent": "redpanda-connect",
      "targetResource": "pipelines",
      "operation": "CREATE",
      "config": {
        "path": "redpanda-pipeline",
        "value": {
          "input": {
            "mqtt": {
              "urls": ["tcp://mqtt-broker:1883"],
              "topics": ["sensors/temperature/#"],
              "client_id": "redpanda-mqtt-consumer",
              "qos": 1,
              "connect_timeout": "30s"
            }
          },
          "pipeline": {
            "processors": [
              {
                "mapping": "root.temperature = this.value.parse_float()\nroot.timestamp = now()\nroot.sensor_id = meta(\"mqtt_topic\").split(\"/\").index(2)"
              }
            ]
          },
          "output": {
            "http_client": {
              "url": "https://api.example.com/measurements",
              "verb": "POST",
              "headers": {
                "Content-Type": "application/json"
              },
              "max_in_flight": 4,
              "timeout": "10s",
              "basic_auth": {
                "username": "api-user",
                "password": "ENC(base64-encrypted-password)"
              }
            }
          }
        }
      }
    }
  }
}
```

### Input Event — SQL Source Pipeline

```json
{
  "specversion": "1.0",
  "type": "de.civitascore.data.pipeline.created",
  "source": "civitas.data.provisioning",
  "id": "event-456",
  "data": {
    "metadata": {
      "messageId": "msg-789",
      "correlationId": "corr-012",
      "resultTopic": "de.civitascore.data.pipeline.processing.result"
    },
    "payload": {
      "targetComponent": "redpanda-connect",
      "targetResource": "pipelines",
      "operation": "CREATE",
      "config": {
        "path": "redpanda-pipeline",
        "value": {
          "input": {
            "sql_raw": {
              "driver": "postgres",
              "dsn": "ENC(base64-encrypted-dsn)",
              "query": "SELECT * FROM measurements WHERE timestamp > $1",
              "args_mapping": "root = [now().ts_sub(3600).format_timestamp(\"2006-01-02T15:04:05Z\")]"
            }
          },
          "pipeline": {
            "processors": [
              {
                "mapping": "root = this"
              }
            ]
          },
          "output": {
            "http_client": {
              "url": "https://api.example.com/data",
              "verb": "POST"
            }
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
  "type": "de.civitascore.data.pipeline.processing.result",
  "source": "civitas.config-adapter.redpanda",
  "id": "result-123",
  "datacontenttype": "application/json",
  "correlationid": "corr-789",
  "originalmessageid": "msg-456",
  "status": "SUCCESS",
  "operation": "CREATE",
  "targetresource": "pipelines",
  "resourceid": "mqtt-to-http-pipeline",
  "data": {
    "correlationId": "corr-789",
    "originalMessageId": "msg-456",
    "status": "SUCCESS",
    "message": "Pipeline created successfully: mqtt-to-http-pipeline",
    "resourceId": "mqtt-to-http-pipeline",
    "operation": "CREATE",
    "targetResource": "pipelines",
    "timestamp": "2026-01-15T10:00:01Z",
    "source": "civitas.config-adapter.redpanda"
  }
}
```

## Credential Encryption

Pipeline configurations may contain sensitive values (database connection strings, API passwords). These can be encrypted and stored as `ENC(...)` values. The adapter decrypts them automatically before sending the pipeline definition to RedPanda Connect.

### How It Works

1. Encrypt the credential with AES-256-GCM using `CIVITAS_MASTER_KEY` (PBKDF2 stretch + HKDF-Expand for per-pipeline key isolation)
2. Wrap the Base64-encoded ciphertext in `ENC(...)` marker
3. The `RedpandaConnectClient` decrypts all `ENC(...)` values recursively before YAML conversion, using the pipeline ID as HKDF context

### Example

```json
{
  "basic_auth": {
    "username": "api-user",
    "password": "ENC(aGVsbG8gd29ybGQ=...)"
  },
  "dsn": "ENC(cG9zdGdyZXM6Ly91c2VyOnBhc3NAaG9zdC9kYg==...)"
}
```

Non-`ENC(...)` string values pass through unchanged.

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `CIVITAS_MASTER_KEY` | 256-bit hex-encoded AES master key (generate with `openssl rand -hex 32`) | Only if `ENC(...)` values are used |

## Pipeline Model

The pipeline definition follows the RedPanda Connect configuration structure:

### Supported Input Types

| Type | Class | Description |
|------|-------|-------------|
| `mqtt` | `MqttInput` | MQTT broker subscription |
| `sql_raw` | `SqlRawInput` | Raw SQL database queries |
| *(extensible)* | `additionalProperties` | Unknown inputs preserved via `@JsonAnySetter` |

### Supported Processors

| Type | Class | Description |
|------|-------|-------------|
| `mapping` | `ProcessorStep` | Bloblang mapping expressions |
| *(extensible)* | `additionalProperties` | Unknown processors preserved via `@JsonAnySetter` |

### Supported Output Types

| Type | Class | Description |
|------|-------|-------------|
| `http_client` | `HttpClientOutput` | HTTP client with auth, rate limiting, timeouts |
| *(extensible)* | `additionalProperties` | Unknown outputs preserved via `@JsonAnySetter` |

All model classes extend `AbstractApiModel` and support `toApiMap()` for recursive Map conversion to YAML.

## Datasource Injection & Placeholder Resolution

In the saga path, pipeline templates can reference datasource properties via placeholders. These are resolved before deployment so that a single pipeline template can be reused across datasets with different datasource configurations.

### DatasourceInjector

When a pipeline's `input.label` matches the pattern `${datasource-id}`, the `DatasourceInjector` replaces the entire input section with a concrete MQTT or SQL input configuration parsed from the matching datasource.

For example, an input label of `${sensor-mqtt-1}` is resolved against the dataset's datasource list. The matching datasource is parsed into a `ConnectorConfig` (MQTT or SQL), and the template's input section is replaced with the fully configured input block.

### PlaceholderResolver

String values anywhere in the pipeline configuration can contain placeholders that are resolved against the dataset context. Three placeholder types are supported:

| Placeholder | Resolves to | Example |
|---|---|---|
| `${FROST_BASE}` | The dataset's `targetUrl` | `https://frost.example.com/FROST-Server/v1.1` |
| `${DATASOURCE[n]}` | Full DSN string from the n-th datasource | `postgres://user@host:5432/db?sslmode=disable` |
| `${DATASOURCE[n].property}` | A single property from the n-th datasource | `${DATASOURCE[0].host}` → `db.example.com` |

#### DSN Construction

When `${DATASOURCE[n]}` is used (without a property suffix), a PostgreSQL DSN is built from the datasource's components:

```
postgres://user@host:port/database?sslmode=<mode>
```

- **Credentials and database** are URL-encoded (RFC 3986)
- **Host** is not URL-encoded (restricted to unreserved characters per RFC 3986)
- **Port** defaults to `5432` if not specified
- **`ssl_mode`** must be one of: `disable`, `allow`, `prefer`, `require`, `verify-ca`, `verify-full`
  - Defaults to `disable` if not specified
  - Invalid values produce a `FatalAdapterException` (routed to DLQ)

#### Bloblang Preservation

Bloblang interpolation expressions (`${!...}`) are preserved and **not** resolved by the adapter. This allows pipeline processors to use Bloblang's native variable interpolation alongside adapter-level placeholders.

## Error Handling

### Idempotency

All three operations (CREATE, UPDATE, DELETE) are idempotent:

| Operation | Idempotent Behavior | HTTP Status |
|-----------|-------------------|-------------|
| CREATE | If pipeline already exists, treated as success | HTTP 409 → Success |
| UPDATE | If pipeline is absent, adapter creates it (upsert) | HTTP 404 → Creates pipeline |
| DELETE | If pipeline is already absent, treated as success | HTTP 404 → Success |

This idempotency ensures operations are safe to retry and can be applied multiple times without unintended side effects.

### HTTP Status Code Mapping

| HTTP Status | Exception Type | Behavior | Notes |
|-------------|----------------|----------|-------|
| 2xx (Success) | — | Operation succeeded | — |
| 409 (Conflict) | — | Treated as success on CREATE | Idempotency: pipeline already exists |
| 404 (Not Found) | — | Treated as success on DELETE | Idempotency: pipeline already absent |
| 404 (Not Found) | `FatalAdapterException` | Falls back to CREATE on UPDATE | Upsert behavior: creates missing pipeline |
| Other 4xx (Client Error) | `FatalAdapterException` | Send to DLQ immediately | Invalid input or missing required fields |
| 5xx (Server Error) | `RetryableAdapterException` | Blocking retry with exponential backoff | Transient server errors |
| Network Error | `RetryableAdapterException` | Blocking retry with exponential backoff | Connection/timeout errors |

### RedPanda-Specific Error Codes

| Code | Name | Description |
|------|------|-------------|
| 3301 | `REDPANDA_ERROR` | RedPanda Connect server error (HTTP 5xx) |
| 3302 | `REDPANDA_PIPELINE_ERROR` | Pipeline operation failed (HTTP 4xx) |
| 3303 | `REDPANDA_DECRYPTION_ERROR` | Credential decryption failed |

### Saga Error Handling

In the saga path, errors are classified by `AbstractSagaCommandHandler`:

| Scenario | Result Type | Description |
|----------|-------------|-------------|
| Forward step succeeds | `STEP_COMPLETED` | Pipeline IDs in `resultData` |
| Forward step fails | `STEP_FAILED` | Error details in `error` field |
| Compensation succeeds | `COMPENSATION_COMPLETED` | Rollback successful |
| Compensation fails | `COMPENSATION_FAILED` | Requires manual intervention |

On `DEPLOY_PIPELINES` failure, already-deployed pipelines are automatically rolled back before returning `STEP_FAILED`.

### Error Response Example

```json
{
  "correlationId": "corr-789",
  "originalMessageId": "msg-456",
  "status": "FAILURE",
  "errorCode": "REDPANDA_PIPELINE_ERROR(3302)",
  "message": "Pipeline operation failed",
  "operation": "CREATE",
  "targetResource": "pipelines",
  "timestamp": "2026-01-15T10:00:01Z"
}
```

## Testing

### Unit Tests

```bash
mvn test -pl config-adapter-redpanda
```

Tests cover:
- `RedpandaConnectClientTest` — HTTP CRUD, YAML conversion, error handling, credential decryption
- `RedpandaAdapterTest` — Event processing, result publishing, error classification
- `RedpandaSagaHandlerTest` — All saga operations, compensation flows, rollback behavior
- `RedpandaAdapterServiceLoaderTest` / `RedpandaSagaHandlerServiceLoaderTest` — ServiceLoader discovery

### Contract Tests

```bash
mvn test -pl config-adapter-redpanda -Dtest=RedpandaSagaContractTest
```

Verifies that `RedpandaSagaHandler` correctly processes the exact payload format produced by `DatasetCommandBuilder` in the orchestrator module. Catches payload format drift between orchestrator and handler.

### Integration Tests

Integration tests use Testcontainers with a real RedPanda Connect instance (requires Docker):

```bash
mvn verify -pl config-adapter-redpanda
```

Test scenarios:
- `RedpandaAdapterIntegrationTest` — CREATE/UPDATE/DELETE pipeline via ConfigAdapter path
- `RedpandaSagaHandlerIntegrationTest` — DEPLOY/UPDATE/DELETE pipelines, compensation flow

## Troubleshooting

### Common Issues

#### 1. Connection Refused

```
Network error for pipeline mqtt-pipeline: Connection refused
```

**Solution:** Verify RedPanda Connect is running and `redpanda.url` is correct. Default port is 4195.

#### 2. Bad Request (400)

```
RedPanda client error during create for pipeline mqtt-pipeline: 400
```

**Solution:** Validate the pipeline YAML configuration. Common causes: invalid Bloblang mapping syntax, unknown input/output types, malformed configuration structure.

#### 3. Pipeline Not Found (404) on Update

```
Signalling for upsert — pipeline not found during update (HTTP 404)
```

**Solution:** This is expected behavior. The adapter automatically creates the pipeline if it doesn't exist (upsert). No action required — the operation succeeds with the pipeline created.

#### 4. Pipeline Not Found (404) on Delete

```
RedPanda client error during delete for pipeline mqtt-pipeline: 404
```

**Solution:** The pipeline does not exist. For `DELETE` operations, this is treated as success (idempotency). The operation completes without error. No action required.

#### 5. Credential Decryption Failed

```
Credential decryption error: AES/GCM/NoPadding decryption failed
```

**Solution:** Verify `CIVITAS_MASTER_KEY` environment variable matches the key used to encrypt the credentials.

#### 6. YAML Serialization Error

```
RedPanda pipeline error: Unrecognized field
```

**Solution:** Check that the pipeline definition matches the expected model structure. Use `additionalProperties` for non-standard RedPanda Connect configuration fields.

## Resources

- [RedPanda Connect Documentation](https://docs.redpanda.com/redpanda-connect/about/)
- [RedPanda Connect Streams API](https://docs.redpanda.com/redpanda-connect/guides/streams_mode/streams_api/)
- [Bloblang Guide](https://docs.redpanda.com/redpanda-connect/guides/bloblang/about/)
- [CloudEvents Specification](https://cloudevents.io/)

## License

European Union Public License (EU-PL) 1.2
