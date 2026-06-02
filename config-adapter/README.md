# Civitas Config Adapter

Modular configuration adapter framework that consumes CloudEvents and applies configuration changes to various backend services.

## Architecture

The project follows the Dependency Inversion Principle with interface-based configuration:

```
┌─────────────────────────────────────┐
│   config-adapter-api                │  Core interfaces
│   - AdapterConfig interface         │  - Property access (for adapters)
│   - ApplicationConfig interface     │  - Extends AdapterConfig
│   - ConfigAdapter                   │  - Adds app-level config
│   - ConfigEvent/ResultEvent         │  - No implementation dependencies
└──────────┬──────────────────────────┘
           │
           │ (implements ApplicationConfig)
           │
┌──────────▼─────────────────────────────┐
│       config-adapter-configuration     │  Configuration implementation
│  - AppConfig (impl ApplicationConfig)  │  - Apache Commons Configuration2
└──────────┬─────────────────────────────┘  - Environment variable support
           │
           │ (compile dependency only in application & tests)
           │
    ┌──────┴──────────┬──────────────────┐
    │                 │                  │
┌───▼────────────┐  ┌─▼──────────────┐ ┌─▼────────────┐
│ event-handler- │  │ event-handler- │ │config-adapter│
│ kafka          │  │ other-broker   │ │ application  │
└────────────────┘  └────────────────┘ └──────┬───────┘
                                              │
                    ┌─────────────────────────┴───────────────────┐
                    │                                             │
         ┌──────────▼─────────────────────┐      ┌────────────────▼───────────────┐
         │ config-adapter-keycloak        │      │      Other implementations     │
         │ (uses AdapterConfig interface) │      │ (uses AdapterConfig interface) │
         └────────────────────────────────┘      └────────────────────────────────┘
```

**Design Benefits:**
- API module has no implementation dependencies
- Adapters depend only on `AdapterConfig` interface, not `AppConfig` implementation
- Implementation details isolated to application module
- Easy to provide alternative AdapterConfig implementations

## Modules

### 1. config-adapter-api
Core interfaces and models that define the adapter contract. **No implementation dependencies.**

**Key Components:**
- `AdapterConfig` - **Interface** for adapter-level configuration (property access only)
- `ApplicationConfig` - **Interface** extending AdapterConfig with application-level config (adapter names, event handlers, health check port)
- `ConfigAdapter` - Interface for backend service adapters
- `AbstractConfigAdapter` - Base class using `AdapterConfig` interface (not `AppConfig` implementation)
- `EventConsumer` - Interface for message consumers
- `EventPublisher` - Interface for publishing result events (uses ConfigResultEvent)
- `ConfigEvent` - Input event data model with metadata and payload
- `ConfigResultEvent` - Output result event model with status, correlation, and error details
- `Config` - Data model for configuration values within events (path and value)
- `Topics` - Constants for all valid Kafka topic names
- `CredentialDecryptor` - AES-256-GCM decryption with PBKDF2 + HKDF key derivation (BSI TR-02102 compliant)
- `CredentialEncryptor` - AES-256-GCM encryption counterpart, produces output compatible with `CredentialDecryptor`
- `CryptoKeyLoader` - Public facade for loading and stretching master keys from environment variables

**Dependency Principle:**
- Adapters use `AdapterConfig` interface for property access
- Application layer uses `ApplicationConfig` interface for app-level configuration
- Allows any configuration implementation to be used
- API remains stable even if configuration implementation changes

**Usage:** Depend on this module to create new adapters or consumers. Adapters work with `AdapterConfig` interface for properties. Application code uses `ApplicationConfig` for app-level configuration.

### 2. config-adapter-configuration
Configuration implementation module using Apache Commons Configuration2.

**Key Components:**
- `AppConfig` - Implementation of `ApplicationConfig` interface with Apache Commons Configuration2
- Environment variable override support
- Properties file support with layered configuration
- Implements both adapter-level (property access) and application-level configuration (adapter names, event handlers, health check)

**Usage:**
- Application module depends on this at compile time to instantiate `AppConfig`
- Adapter modules only need this for tests (test scope dependency)
- Adapters use `AdapterConfig` interface at runtime for property access
- Application code uses `ApplicationConfig` interface for app-level configuration

### 3. event-handler-kafka
Kafka-specific message consumer / publisher implementation.

**Key Components:**
- `KafkaEventHandler` - Consumes CloudEvents from Kafka and implements EventPublisher
- `CloudEventProcessor` - Internal helper that deserializes CloudEvents and delegates to ConfigAdapter. Throws exceptions on processing failures to allow caller-defined error handling.

**Usage:** Use this module to consume events from Apache Kafka and publish results.

### 4. config-adapter-application
Generic application runner that uses ServiceLoader to discover and load adapters and consumers by name.

**Key Components:**
- `Application` - Main entry point that reads configuration and wires components
- Instantiates `AppConfig` (the only module that needs the concrete implementation)

**Features:**
- Supports single or multiple adapters configuration
- Each adapter is injected into its own EventConsumer
- Consumer manages complete lifecycle (adapter + consumer)
- Adapters declare which Kafka topics they want to subscribe to via `getSubscribedTopics()`
- Simplified architecture with cleaner lifecycle management
- Automatic resource cleanup on shutdown

**Dependency Note:**
- Only module with compile-time dependency on `config-adapter-configuration`
- Instantiates `AppConfig` and uses it via `ApplicationConfig` interface for application setup
- Passes `AppConfig` as `AdapterConfig` interface to adapters (adapters only see property access methods)
- Demonstrates Dependency Inversion Principle and Interface Segregation Principle in practice

**Usage:** Configure adapters via application.properties using comma-separated list.

### 5. config-adapter-keycloak
Production implementation of Keycloak adapter.

**Key Components:**
- `KeycloakAdapter` - Manages Keycloak configuration via REST API

**Usage:** Production-ready adapter that integrates with Keycloak for user, realm, and client management.

### 6. config-adapter-apisix
Production implementation of APISIX adapter for managing API Gateway upstreams.

**Key Components:**
- `ApisixAdapter` - Manages APISIX upstream configuration via Admin API

**Supported Operations:**
- CREATE - Create new APISIX upstreams
- UPDATE - Update existing APISIX upstreams
- DELETE - Delete APISIX upstreams

**Subscribed Topics:**
- `de.civitascore.api.backend.created`
- `de.civitascore.api.backend.updated`
- `de.civitascore.api.backend.deleted`

**Configuration Properties:**
```properties
# APISIX Admin API URL (default: http://localhost:9180)
apisix.admin.url=http://localhost:9180

# APISIX Admin API Key (default: edd1c9f034335f136f87ad84b625c8f1)
apisix.admin.key=edd1c9f034335f136f87ad84b625c8f1

# Topics to subscribe to
apisix.topics=de.civitascore.api.backend.created,de.civitascore.api.backend.updated,de.civitascore.api.backend.deleted
```

**Usage:** Production-ready adapter that integrates with Apache APISIX API Gateway for managing upstream backend services.

**Documentation:** For detailed documentation including event formats, error handling, and integration examples, see [APISIX Adapter Documentation](config-adapter-apisix/README.md).

### 7. config-adapter-redpanda
Production implementation of RedPanda Connect adapter for managing data pipelines.

**Key Components:**
- `RedpandaAdapter` - Manages RedPanda Connect data pipelines via Streams API
- `RedpandaSagaHandler` - Saga orchestration for multi-pipeline dataset operations

**Supported Operations:**
- CREATE - Create new data pipelines (idempotent: HTTP 409 → success)
- UPDATE - Update existing pipelines or create if absent (upsert)
- DELETE - Delete pipelines (idempotent: HTTP 404 → success)

**Subscribed Topics:**
- `de.civitascore.data.pipeline.created`
- `de.civitascore.data.pipeline.updated`
- `de.civitascore.data.pipeline.deleted`

**Saga Topics:**
- `de.civitascore.dataset.redpanda.execute`
- `de.civitascore.dataset.redpanda.compensate`

**Configuration Properties:**
```properties
# RedPanda Connect Streams API URL (default: http://localhost:4195)
redpanda.url=http://localhost:4195

# Topics to subscribe to
redpanda.topics=de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted

# Master key for encrypted credentials (optional, only needed for ENC(...) values)
# CIVITAS_MASTER_KEY=<256-bit hex-encoded key>
```

**Features:**
- Full CRUD operations for RedPanda Connect data pipelines
- Automatic JSON-to-YAML conversion for pipeline definitions
- AES-256-GCM credential decryption for sensitive pipeline configuration
- Idempotent operations with automatic conflict and not-found handling
- Saga orchestration with forward execution and compensation (rollback)
- Datasource injection and placeholder resolution for reusable pipeline templates
- Comprehensive error handling with HTTP status-based categorization

**Usage:** Production-ready adapter that integrates with RedPanda Connect for streaming data pipeline management.

**Documentation:** For detailed documentation including event formats, credential encryption, error handling, and integration examples, see [RedPanda Adapter Documentation](config-adapter-redpanda/README.md).

### 8. config-adapter-examples
Example adapter implementations for reference and testing.

**Key Components:**
- `DummyLogAdapter` - Simple example adapter that only logs events

**Usage:** Reference implementations for learning how to create adapters. DummyLogAdapter shows a minimal implementation useful for testing and as a starting point for new adapters.

## Quick Start

### Build All Modules

```bash
mvn clean install
```

### Run Application

```bash
cd config-adapter-application
java -jar target/config-adapter-application-1.0.0-SNAPSHOT.jar
```

## Configuring Multiple Adapters

The framework supports configuring multiple adapters to run independently. Each adapter gets its own dedicated Kafka consumer subscribed to its specific topics.

### Configuration Options

**Multiple Adapters (comma-separated short names)**
```properties
adapters=keycloak,apisix,redpanda,dummylog
eventhandler.name=kafka
```

### How It Works

1. **Topic Subscription**: Each adapter declares which Kafka topics it wants to subscribe to via `getSubscribedTopics()`
2. **Adapter Injection**: Each adapter is injected into its own `EventConsumer` during construction
3. **Lifecycle Management**: The consumer manages both its own lifecycle and the adapter's lifecycle
4. **Independent Processing**: Each consumer runs independently - one failure doesn't affect others
5. **Efficient Consumption**: Only relevant events are consumed from Kafka (filtered at broker level, not after consumption)
6. **Resource Management**: Consumers properly close both themselves and their adapters on shutdown

### Example Configuration

```properties
# Multiple adapters - each will get its own consumer (short names matched via ServiceLoader)
adapters=keycloak,apisix,dummylog

# Event handler name (implements both EventConsumer and EventPublisher)
eventhandler.name=kafka

# OR use separate consumer/publisher (publisher is optional):
# eventconsumer.name=kafka
# eventpublisher.name=kafka

# Kafka settings
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Keycloak settings
keycloak.url=http://localhost:8080
keycloak.realm=master
keycloak.username=admin
keycloak.password=admin
keycloak.client.id=admin-cli

# APISIX settings
apisix.admin.url=http://localhost:9180
apisix.admin.key=edd1c9f034335f136f87ad84b625c8f1
apisix.topics=de.civitascore.api.backend.created,de.civitascore.api.backend.updated,de.civitascore.api.backend.deleted

# DummyLogAdapter topic configuration
dummylog.topics=de.civitascore.idm.user.created,de.civitascore.idm.user.updated,de.civitascore.idm.user.deleted
```

### Topic Subscription Example

```java
public class MyAdapter extends AbstractConfigAdapter {

    private static final String ADAPTER_NAME = "myadapter";

    @Override
    public String getName() {
        return ADAPTER_NAME;  // Used for ServiceLoader discovery
    }

    @Override
    public List<String> getSubscribedTopics() {
        // Read topics from configuration (e.g., myadapter.topics property)
        return getTopicsFromConfig(ADAPTER_NAME + ".topics");
    }

    @Override
    public void processConfigEvent(String topic, ConfigEvent event) {
        // Access event data via event.payload() and event.metadata()
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        // Store publisher to send result events
    }

    @Override
    public void close() {
        // Cleanup resources
    }
}
```

## Creating a New Adapter

### 1. Add Dependencies

```xml
<dependency>
    <groupId>de.civitascore</groupId>
    <artifactId>config-adapter-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Implement ConfigAdapter

```java
import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.messaging.EventPublisher;

import java.util.List;

public class MyServiceAdapter extends AbstractConfigAdapter {

    private static final String ADAPTER_NAME = "myservice";
    private EventPublisher eventPublisher;

    @Override
    public String getName() {
        return ADAPTER_NAME;  // Must match the name in adapters= config
    }

    @Override
    public List<String> getSubscribedTopics() {
        // Read topics from config (e.g., myservice.topics=de.civitascore.idm.user.created,...)
        return getTopicsFromConfig(ADAPTER_NAME + ".topics");
    }

    @Override
    public void processConfigEvent(String topic, ConfigEvent event) {
        // Your implementation
        switch (event.payload().operation().toUpperCase()) {
            case "CREATE" -> handleCreate(event);
            case "UPDATE" -> handleUpdate(event);
            case "DELETE" -> handleDelete(event);
        }
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    private void publishResult(ConfigEvent original, boolean success, String message) {
        if (eventPublisher == null || original.metadata().resultTopic() == null) {
            return;
        }

        ConfigResultEvent result = success
            ? ConfigResultEvent.success(
                  original.metadata().correlationId(),
                  original.metadata().messageId(),
                  message,
                  resourceId,
                  original.payload().operation(),
                  original.payload().targetResource(),
                  "my.adapter.source")
            : ConfigResultEvent.failure(
                  original.metadata().correlationId(),
                  original.metadata().messageId(),
                  errorCode,
                  message,
                  original.payload().operation(),
                  original.payload().targetResource(),
                  "my.adapter.source");

        eventPublisher.publish(original.metadata().resultTopic(), result);
    }

    @Override
    public void close() {
        // Cleanup resources
    }
}
```

### 3. Register via ServiceLoader

Create file `src/main/resources/META-INF/services/de.civitascore.configadapter.adapter.ConfigAdapter`:
```
com.mycompany.MyServiceAdapter
```

### 4. Configure application.properties

```properties
# Application Configuration (short names matched via ServiceLoader)
adapters=myservice
eventhandler.name=kafka

# Kafka Configuration
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Your Service Configuration
myservice.topics=de.civitascore.idm.user.created,de.civitascore.idm.user.updated
myservice.url=http://localhost:8080
myservice.api.key=your-api-key
```

The `Application` class from `config-adapter-application` will automatically:
1. Discover your adapter via ServiceLoader by matching the name "myservice"
2. Call `initialize()` to inject configuration
3. Create an EventConsumer (also discovered via ServiceLoader by name "kafka")
4. Subscribe to topics returned by `getSubscribedTopics()`
5. Start the consumer and manage the complete lifecycle

## Creating a New Event Consumer

### 1. Implement EventConsumer

```java
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.messaging.EventConsumer;
import de.civitascore.event.handler.kafka.CloudEventProcessor;

public class RabbitMQEventConsumer implements EventConsumer {

    private static final String CONSUMER_NAME = "rabbitmq";
    private ConfigAdapter adapter;
    private CloudEventProcessor processor;
    private ApplicationConfig config;

    @Override
    public String getName() {
        return CONSUMER_NAME;  // Used for ServiceLoader discovery
    }

    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {
        this.config = config;
        this.adapter = adapter;
        this.processor = new CloudEventProcessor(adapter);
        // Initialize RabbitMQ connection and subscribe to adapter.getSubscribedTopics()
    }

    @Override
    public void start() {
        // Start consuming messages from subscribed topics (non-blocking)
        // Call processor.handleEvent(topic, cloudEvent) for each message
        // Note: processor.handleEvent() throws exceptions - handle them appropriately
    }

    @Override
    public void close() {
        // Cleanup RabbitMQ connection
        // Close the adapter
        try {
            adapter.close();
        } catch (Exception e) {
            // Handle error
        }
    }
}
```

### 2. Register via ServiceLoader

Create file `src/main/resources/META-INF/services/de.civitascore.configadapter.messaging.EventConsumer`:
```
com.mycompany.RabbitMQEventConsumer
```

### 3. Configure in application.properties

```properties
# Use your custom consumer (matched by name via ServiceLoader)
eventconsumer.name=rabbitmq

# Adapters to run
adapters=keycloak
```

The Application class will automatically:
1. Discover your consumer via ServiceLoader by matching the name "rabbitmq"
2. Call `initialize()` with config and adapter
3. Call `start()` to begin consuming messages

## CloudEvent Format

### Event Type Convention

Events follow the pattern: `de.civitascore.idm.{resource}.{action}`

Examples (from `Topics` constants):
- `de.civitascore.idm.user.created`
- `de.civitascore.idm.user.updated`
- `de.civitascore.idm.user.deleted`
- `de.civitascore.idm.realm.created`
- `de.civitascore.idm.client.updated`

All available topic constants are defined in `de.civitascore.configadapter.Topics`.

### Configuration Split Pattern

The framework supports two configuration patterns for event handling:

#### Combined Handler Pattern (Recommended)

Use a single handler that implements both `EventConsumer` and `EventPublisher`:

```properties
eventhandler.name=kafka
```

This is the recommended approach when both consumer and publisher use the same technology (e.g., Kafka).

#### Separate Consumer/Publisher Pattern

Use different implementations for consuming and publishing:

```properties
eventconsumer.name=kafka
eventpublisher.name=rabbitmq
```

This pattern is useful when:
- Events are consumed from one broker but results published to another
- Different configurations are needed for consuming vs. publishing
- Testing scenarios with mock publishers

**Conflict Detection:**
- If `eventhandler.name` is set, `eventconsumer.name` and `eventpublisher.name` are ignored
- If both patterns are partially set, the application logs a warning

### Event Structure

```json
{
  "ce_specversion": "1.0",
  "ce_type": "de.civitascore.idm.user.created",
  "ce_source": "manual.test",
  "ce_id": "msg-123",
  "ce_time": "2026-01-15T10:35:00+00:00",
  "content-type": "application/json"
  "data": {
    "metadata": {
      "messageId": "msg-123",
      "timestamp": "2026-01-15T10:35:00+00:00",
      "source": "idm.service",
      "correlationId": "corr-456",
      "configVersion": "1.0",
      "resultTopic": "de.civitascore.idm.processing.result"
    },
    "payload": {
      "targetComponent": "user",
      "targetResource": "civitas-core",
      "operation": "CREATE",
      "config": {
        "path": "realms/civitas-core/users/user-789",
        "value": {
          "resourceType": "user",
          "realmId": "civitas-core",
          "username": "john.doe",
          "email": "john@example.com",
          "enabled": true
        }
      }
    }
  }
}
```

### CloudEvent Result Format

Result events are published as CloudEvents with extension attributes for correlation and status tracking.

#### Result Event Extension Attributes

| Extension Attribute | Description | Example |
|---------------------|-------------|---------|
| `correlationid` | Correlation ID from original event | `"corr-456"` |
| `originalmessageid` | Message ID from original event | `"msg-123"` |
| `status` | Processing status | `"SUCCESS"` or `"FAILURE"` |
| `operation` | Operation performed | `"CREATE"`, `"UPDATE"`, `"DELETE"` |
| `targetresource` | Target resource path | `"civitas-core"` |
| `message` | Status message | `"User created successfully"` |
| `resourceid` | Created/updated resource ID (SUCCESS only) | `"user-789"` |
| `errorcode` | Error code identifier (FAILURE only) | `"KEYCLOAK_USER_ERROR(3004)"` |
| `errormessage` | Error description (FAILURE only) | `"User operation failed"` |

#### Success Result CloudEvent Example

```json
{
  "specversion": "1.0",
  "id": "result-uuid-123",
  "type": "de.civitascore.idm.processing.result",
  "source": "civitas.config-adapter.keycloak",
  "time": "2026-01-15T10:35:01+00:00",
  "datacontenttype": "application/json",
  "correlationid": "corr-456",
  "originalmessageid": "msg-123",
  "status": "SUCCESS",
  "operation": "CREATE",
  "targetresource": "civitas-core",
  "resourceid": "user-789",
  "message": "USER_CREATE_SUCCESS",
  "data": {
    "correlationId": "corr-456",
    "originalMessageId": "msg-123",
    "status": "SUCCESS",
    "message": "USER_CREATE_SUCCESS",
    "resourceId": "user-789",
    "operation": "CREATE",
    "targetResource": "civitas-core",
    "timestamp": "2026-01-15T10:35:01+00:00",
    "source": "civitas.config-adapter.keycloak"
  }
}
```

#### Failure Result CloudEvent Example

```json
{
  "specversion": "1.0",
  "id": "result-uuid-456",
  "type": "de.civitascore.idm.processing.result",
  "source": "civitas.config-adapter.keycloak",
  "time": "2026-01-15T10:35:01+00:00",
  "datacontenttype": "application/json",
  "correlationid": "corr-456",
  "originalmessageid": "msg-123",
  "status": "FAILURE",
  "operation": "CREATE",
  "targetresource": "civitas-core",
  "errorcode": "KEYCLOAK_USER_ERROR(3004)",
  "errormessage": "User operation failed",
  "data": {
    "correlationId": "corr-456",
    "originalMessageId": "msg-123",
    "status": "FAILURE",
    "message": "User operation failed",
    "operation": "CREATE",
    "targetResource": "civitas-core",
    "errorCode": "KEYCLOAK_USER_ERROR(3004)",
    "timestamp": "2026-01-15T10:35:01+00:00",
    "source": "civitas.config-adapter.keycloak"
  }
}
```

## Module Details

### config-adapter-api

Pure interfaces with minimal dependencies:
- CloudEvents API
- Jackson (for data binding)
- SLF4J (for logging)

Contains:
- `AdapterConfig` interface for adapter-level configuration (property access)
- `ApplicationConfig` interface extending AdapterConfig with application-level configuration
- `ConfigAdapter` interface with `getSubscribedTopics()` and `setEventPublisher()` methods
- `EventConsumer` interface for message consumers
- `EventPublisher` interface for publishing `ConfigResultEvent`
- `ConfigEvent` record with metadata and payload structure (input events)
- `ConfigResultEvent` record for adapter processing results (output events)
- `Topics` class with centralized topic constants
- `CloudEventProcessor` internal helper for deserialization (throws exceptions on failure)
- `CredentialDecryptor` for AES-256-GCM decryption of `ENC(...)` credential values (PBKDF2 + HKDF key derivation)
- `CredentialEncryptor` for AES-256-GCM encryption, producing `ENC(...)` wrapped values
- `CryptoKeyLoader` for loading and stretching master keys from environment variables

No dependencies on implementation modules - pure interfaces only.

### config-adapter-configuration

Configuration management module:
- Apache Commons Configuration2
- Commons BeanUtils

Contains:
- `AppConfig` class implementing `ApplicationConfig` interface
- Environment variable override support
- Layered configuration (environment variables over properties files)
- Property key to environment variable name conversion
- Provides both adapter-level and application-level configuration

No dependencies on other config-adapter modules - pure configuration infrastructure.

### event-handler-kafka

Kafka-specific implementation:
- Depends on: `config-adapter-api`
- Kafka Clients
- CloudEvents Kafka binding

Contains:
- `KafkaEventHandler` with constructor accepting `(AppConfig, ConfigAdapter)`
- Implements both EventConsumer and EventPublisher interfaces
- Converts `ConfigResultEvent` to CloudEvent for Kafka transmission
- Subscribes to multiple Kafka topics at broker level
- Uses virtual threads for efficient concurrent consumption

No backend service dependencies.

### config-adapter-application

Generic application runner:
- Depends on: `config-adapter-api`, `config-adapter-configuration`
- Uses ServiceLoader to discover and load adapters and consumers by name
- Uses `AppConfig` from configuration module for property management

Contains:
- `Application` main class with simplified architecture
- Creates one consumer per adapter, injecting adapter into consumer
- Consumer manages complete lifecycle (both consumer and adapter)
- Graceful shutdown with proper resource cleanup
- Supports both single adapter (backward compatible) and multiple adapters

No consumer or adapter implementation dependencies.

### config-adapter-keycloak

Production Keycloak adapter:
- Depends on: `config-adapter-api`
- Keycloak Admin Client

Contains:
- `KeycloakAdapter` - Full Keycloak integration (subscribes to 13 topics: user, realm, and client operations)
- Manages realms, clients, and users via Keycloak REST API

### config-adapter-examples

Example adapter implementations:
- Depends on: `config-adapter-api`

Contains:
- `DummyLogAdapter` - Simple logging example (subscribes to 3 user topics)
- Minimal implementation showing adapter basics

Reference implementations for learning and creating your own adapters.

## Configuration

### Environment Variable Support

Configuration is powered by **Apache Commons Configuration2**, providing robust support for environment variables and multiple configuration sources. All properties can be overridden by environment variables - essential for containerized deployments (Docker, Kubernetes).

**Conversion Rules:**
- Convert property key to **UPPERCASE**
- Replace dots (`.`) with underscores (`_`)
- Replace dashes (`-`) with underscores (`_`)

**Resolution Order (highest priority first):**
1. Environment variable (e.g., `KAFKA_BOOTSTRAP_SERVERS`)
2. Properties file value (e.g., `kafka.bootstrap.servers` in application.properties)
3. Default value (if specified in code)

**Property to Environment Variable Mapping:**

| Property | Environment Variable |
|----------|---------------------|
| `healthcheck.port` | `HEALTHCHECK_PORT` |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` |
| `kafka.group.id` | `KAFKA_GROUP_ID` |
| `keycloak.url` | `KEYCLOAK_URL` |
| `keycloak.realm` | `KEYCLOAK_REALM` |
| `keycloak.username` | `KEYCLOAK_USERNAME` |
| `keycloak.password` | `KEYCLOAK_PASSWORD` |
| `keycloak.client.id` | `KEYCLOAK_CLIENT_ID` |
| `adapters` | `ADAPTERS` |
| `eventhandler.name` | `EVENTHANDLER_NAME` |
| `eventconsumer.name` | `EVENTCONSUMER_NAME` |
| `eventpublisher.name` | `EVENTPUBLISHER_NAME` |
| `kafka.publish.timeout.ms` | `KAFKA_PUBLISH_TIMEOUT_MS` |
| `kafka.retry.max.attempts` | `KAFKA_RETRY_MAX_ATTEMPTS` |
| `kafka.retry.initial.backoff.ms` | `KAFKA_RETRY_INITIAL_BACKOFF_MS` |
| `kafka.dlq.topic` | `KAFKA_DLQ_TOPIC` |

### Health Check Endpoints

The application provides HTTP health check endpoints for external monitoring:

**Endpoints:**

| Endpoint | Description | Success | Failure |
|----------|-------------|---------|---------|
| `/health` | Detailed health status | 200 | 503 |
| `/health/ready` | Kubernetes readiness probe | 200 | 503 |
| `/health/live` | Kubernetes liveness probe | 200 | - |

**Response Examples:**

```json
// GET /health (healthy)
{
  "status": "UP",
  "consumers": 2,
  "details": {
    "ready": true
  }
}

// GET /health/ready
{"status": "UP"}

// GET /health/live
{"status": "UP"}
```

**Configuration:**
```properties
# Health check port (default: 8080)
healthcheck.port=8080
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  config-adapter:
    image: config-adapter:latest
    ports:
      - "8080:8080"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      KAFKA_GROUP_ID: config-adapter-prod
      KEYCLOAK_URL: http://keycloak:8080
      KEYCLOAK_REALM: production
      KEYCLOAK_USERNAME: admin
      KEYCLOAK_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      KEYCLOAK_CLIENT_ID: admin-cli
      HEALTHCHECK_PORT: "8080"
      ADAPTERS: keycloak
      EVENTHANDLER_NAME: kafka
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s
    depends_on:
      - kafka
      - keycloak
```

### Kubernetes Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-adapter
spec:
  replicas: 1
  selector:
    matchLabels:
      app: config-adapter
  template:
    metadata:
      labels:
        app: config-adapter
    spec:
      containers:
      - name: config-adapter
        image: config-adapter:latest
        ports:
        - name: health
          containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        - name: KEYCLOAK_URL
          value: "http://keycloak:8080"
        - name: KEYCLOAK_PASSWORD
          valueFrom:
            secretKeyRef:
              name: keycloak-secrets
              key: admin-password
        livenessProbe:
          httpGet:
            path: /health/live
            port: health
          initialDelaySeconds: 10
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /health/ready
            port: health
          initialDelaySeconds: 5
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
```

### Kafka Configuration

```properties
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Optional: Retry and publish configuration
kafka.publish.timeout.ms=5000              # Timeout for synchronous publish (default: 5000ms)
kafka.retry.max.attempts=3                 # Max retry attempts before DLQ (default: 3)
kafka.retry.initial.backoff.ms=1000        # Initial backoff between retries (default: 1000ms)
kafka.dlq.topic=de.civitascore.idm.dlq       # Dead Letter Queue topic (default: de.civitascore.idm.dlq)
```

Note: Individual topics are not configured in properties. Each adapter declares its own topics via `getSubscribedTopics()`.

### Error Handling & Retry Behavior

The Kafka event handler implements robust error handling with blocking retries and Dead Letter Queue (DLQ) support.

#### Exception Types

| Exception Type | Behavior | Examples |
|----------------|----------|----------|
| `RetryableAdapterException` | Blocking retry with exponential backoff | Network timeouts, HTTP 5xx, rate limiting |
| `FatalAdapterException` | Sent directly to DLQ | Validation errors, HTTP 4xx, resource not found |

#### Retry Algorithm

```
backoff = initialBackoffMs × 2^(attempt-1)
```

- **Initial backoff:** Configurable via `kafka.retry.initial.backoff.ms` (default: 1000ms)
- **Maximum backoff:** Capped at 30 seconds
- **Max attempts:** Configurable via `kafka.retry.max.attempts` (default: 3)

Example retry sequence with defaults:
1. Attempt 1 fails → wait 1000ms
2. Attempt 2 fails → wait 2000ms
3. Attempt 3 fails → wait 4000ms (capped at 30s)
4. Max retries exceeded → send to DLQ

#### Dead Letter Queue (DLQ) Event Format

When an event fails all retry attempts or encounters a fatal error, it is sent to the DLQ topic with additional metadata:

| Extension Attribute | Description |
|---------------------|-------------|
| `dlqerrorcode` | Numeric error code (e.g., "1001", "2001") |
| `dlqerrormsg` | Safe external error message (no PII, no stack traces) |
| `dlqoriginaltopic` | Original Kafka topic the event came from |
| `dlqtimestamp` | ISO 8601 timestamp when sent to DLQ |
| `dlqretrycount` | Number of retry attempts made before DLQ |

**Important:** DLQ publishing is synchronous to ensure no message loss. If DLQ send fails, the original event will be reprocessed on the next poll.

#### Saga Consumer Retry Behavior

The saga consumers (`SagaResultConsumer`, `SagaTriggerConsumer`) use the same retry algorithm via `ConsumerRecordRetry`. (The legacy custom-orchestrator `KafkaSagaCommandConsumer` has been removed — Flowable is now the sole saga engine.) Permanent errors (e.g., malformed JSON → `IOException`) are skipped immediately. Transient errors (e.g., engine/publish failures → `RuntimeException`) are retried with exponential backoff up to 3 attempts. After max retries, the record is skipped and committed. All saga consumers use **per-record commits** (not batch commits) to ensure a single poison-pill record cannot block the consumer. The saga timeout mechanism handles recovery by triggering compensation. No DLQ is used for saga consumers.

#### Failure Result Event

In addition to DLQ, a failure result event is published to the `resultTopic` (if specified in the original event metadata):

```json
{
  "correlationId": "corr-456",
  "originalMessageId": "msg-123",
  "status": "FAILURE",
  "errorCode": "KEYCLOAK_USER_ERROR(3004)",
  "message": "User operation failed",
  "operation": "CREATE",
  "targetResource": "civitas-core",
  "timestamp": "2026-01-15T10:35:00+00:00",
  "source": "civitas.config-adapter.keycloak"
}
```

### Error Codes Reference

Error codes are categorized by type and severity:

#### 1xxx - Validation/Fatal Errors (→ DLQ immediately)

| Code | Name | Retryable | Internal Log Template | External Message |
|------|------|-----------|----------------------|------------------|
| 1001 | `INVALID_PAYLOAD` | No | Invalid payload: %s | Validation failed |
| 1002 | `RESOURCE_NOT_FOUND` | No | Resource not found: %s | Resource not found |
| 1003 | `MISSING_CONFIG` | No | Missing config: %s | Configuration error |
| 1004 | `UNSUPPORTED_OPERATION` | No | Operation %s not supported | Operation not supported |
| 1005 | `INVALID_RESOURCE_TYPE` | No | Invalid resource type: %s | Invalid resource type |

#### 2xxx - Connectivity/Retryable Errors (→ Blocking retry loop)

| Code | Name | Retryable | Internal Log Template | External Message |
|------|------|-----------|----------------------|------------------|
| 2001 | `CONNECTION_TIMEOUT` | Yes | Connection timeout to %s | Service temporarily unavailable |
| 2002 | `SERVICE_UNAVAILABLE` | Yes | Service %s unavailable: HTTP %d | Service temporarily unavailable |
| 2003 | `NETWORK_ERROR` | Yes | Network error to %s: %s | Service temporarily unavailable |
| 2004 | `RATE_LIMITED` | Yes | Rate limited by %s | Service temporarily unavailable |
| 2005 | `PUBLISH_ERROR` | Yes | Failed to publish event to %s: %s | Message delivery failed |
| 2006 | `PUBLISH_TIMEOUT` | Yes | Publish timeout to %s after %d ms | Message delivery timeout |

#### 3xxx - Adapter-Specific Errors

**Keycloak Adapter (3001-3007):**

| Code | Name | Retryable | Internal Log Template | External Message |
|------|------|-----------|----------------------|------------------|
| 3001 | `KEYCLOAK_ERROR` | No | Keycloak error: %s | Identity provider error |
| 3002 | `KEYCLOAK_CONFLICT` | No | Keycloak conflict: %s | Resource already exists |
| 3003 | `KEYCLOAK_REALM_ERROR` | No | Keycloak realm error: %s | Realm operation failed |
| 3004 | `KEYCLOAK_USER_ERROR` | No | Keycloak user error: %s | User operation failed |
| 3005 | `KEYCLOAK_CLIENT_ERROR` | No | Keycloak client error: %s | Client operation failed |
| 3006 | `KEYCLOAK_ROLE_ERROR` | No | Keycloak role error: %s | Role operation failed |
| 3007 | `KEYCLOAK_GROUP_ERROR` | No | Keycloak group error: %s | Group operation failed |

**APISIX Adapter (3101-3103):**

| Code | Name | Retryable | Internal Log Template | External Message |
|------|------|-----------|----------------------|------------------|
| 3101 | `APISIX_ERROR` | No | APISIX error: %s | Gateway error |
| 3102 | `APISIX_ROUTE_ERROR` | No | APISIX route error: %s | Route operation failed |
| 3103 | `APISIX_UPSTREAM_ERROR` | No | APISIX upstream error: %s | Upstream operation failed |

#### 9xxx - Unknown/Unexpected Errors

| Code | Name | Retryable | Internal Log Template | External Message |
|------|------|-----------|----------------------|------------------|
| 9001 | `UNKNOWN_ERROR` | No | Unexpected error: %s | Internal error |
| 9002 | `SERIALIZATION_ERROR` | No | Serialization error: %s | Data processing error |
| 9003 | `DESERIALIZATION_ERROR` | No | Deserialization error: %s | Data processing error |

### Valid Kafka Topics

All topics are defined in `de.civitascore.configadapter.Topics` and validated at startup.

#### User Events

| Topic Constant | Topic Value |
|----------------|-------------|
| `USER_CREATED` | `de.civitascore.idm.user.created` |
| `USER_UPDATED` | `de.civitascore.idm.user.updated` |
| `USER_DELETED` | `de.civitascore.idm.user.deleted` |
| `USER_LOCKED` | `de.civitascore.idm.user.locked` |
| `USER_UNLOCKED` | `de.civitascore.idm.user.unlocked` |
| `USER_PASSWORD_CHANGED` | `de.civitascore.idm.user.password.changed` |
| `USER_PASSWORD_RESET` | `de.civitascore.idm.user.password.reset` |

#### Realm Events

| Topic Constant | Topic Value |
|----------------|-------------|
| `REALM_CREATED` | `de.civitascore.idm.realm.created` |
| `REALM_UPDATED` | `de.civitascore.idm.realm.updated` |
| `REALM_DELETED` | `de.civitascore.idm.realm.deleted` |

#### Client Events

| Topic Constant | Topic Value |
|----------------|-------------|
| `CLIENT_CREATED` | `de.civitascore.idm.client.created` |
| `CLIENT_UPDATED` | `de.civitascore.idm.client.updated` |
| `CLIENT_DELETED` | `de.civitascore.idm.client.deleted` |

#### Group Events

| Topic Constant | Topic Value |
|----------------|-------------|
| `GROUP_CREATED` | `de.civitascore.idm.group.created` |
| `GROUP_UPDATED` | `de.civitascore.idm.group.updated` |
| `GROUP_DELETED` | `de.civitascore.idm.group.deleted` |

#### Role Events

| Topic Constant | Topic Value |
|----------------|-------------|
| `ROLE_CREATED` | `de.civitascore.idm.role.created` |
| `ROLE_UPDATED` | `de.civitascore.idm.role.updated` |
| `ROLE_DELETED` | `de.civitascore.idm.role.deleted` |

#### Backend Events (APISIX)

| Topic Constant | Topic Value |
|----------------|-------------|
| `BACKEND_CREATED` | `de.civitascore.api.backend.created` |
| `BACKEND_UPDATED` | `de.civitascore.api.backend.updated` |
| `BACKEND_DELETED` | `de.civitascore.api.backend.deleted` |

#### Route Events (APISIX)

| Topic Constant | Topic Value |
|----------------|-------------|
| `ROUTE_CREATED` | `de.civitascore.api.route.created` |
| `ROUTE_UPDATED` | `de.civitascore.api.route.updated` |
| `ROUTE_DELETED` | `de.civitascore.api.route.deleted` |

**Topic Validation:**
- Topics are validated using `Topics.isValidTopic(String)` method
- Whitespace is trimmed automatically
- Invalid topics throw `IllegalArgumentException` at startup
- Use `Topics.ALL_TOPICS` for a list of all valid topic values

### Example: Single Adapter Configuration

```properties
# Application settings (short names matched via ServiceLoader)
adapters=keycloak
eventhandler.name=kafka

# Kafka settings
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Keycloak settings
keycloak.url=http://localhost:8080
keycloak.realm=master
keycloak.username=admin
keycloak.password=admin
keycloak.client.id=admin-cli
keycloak.topics=de.civitascore.idm.user.created,de.civitascore.idm.user.updated,de.civitascore.idm.realm.created
```

### Example: Multiple Adapters Configuration

```properties
# Run both KeycloakAdapter and DummyLogAdapter (short names)
adapters=keycloak,dummylog
eventhandler.name=kafka

# Kafka settings
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Keycloak settings (only needed by KeycloakAdapter)
keycloak.url=http://localhost:8080
keycloak.realm=master
keycloak.username=admin
keycloak.password=admin
keycloak.client.id=admin-cli
keycloak.topics=de.civitascore.idm.user.created,de.civitascore.idm.user.updated

# DummyLogAdapter topic configuration
dummylog.topics=de.civitascore.idm.user.created,de.civitascore.idm.user.updated,de.civitascore.idm.user.deleted
```

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

Integration tests use Testcontainers for Kafka and Keycloak.

### Parallel Test Execution

Run tests with two parallel module threads for faster execution (~30% faster):

```bash
mvn test -T 2
```

This works because the module tests are independent and Testcontainers uses random ports.

### Building the Fat JAR

Adapter plugins (APISIX, FROST, RedPanda, Examples) are only included in the fat JAR via the `dist` profile:

```bash
mvn package -Pdist
```

## Development

### Project Structure

```
civitas-config-adapter/
├── pom.xml                                    # Parent POM
├── config-adapter-api/
│   ├── pom.xml
│   └── src/main/java/com/civitas/configadapter/
│       ├── Topics.java
│       ├── ConfigBase.java
│       ├── adapter/
│       │   ├── ConfigAdapter.java
│       │   └── AbstractConfigAdapter.java
│       ├── configuration/
│       │   ├── AdapterConfig.java             # Interface for adapter configuration
│       │   └── ApplicationConfig.java         # Interface for application configuration
│       ├── messaging/
│       │   ├── EventBase.java
│       │   ├── EventConsumer.java
│       │   └── EventPublisher.java
│       └── model/
│           ├── ConfigEvent.java
│           ├── ConfigResultEvent.java
│           ├── Metadata.java
│           ├── Payload.java
│           └── Config.java                    # Event config data model
├── config-adapter-configuration/
│   ├── pom.xml
│   └── src/main/java/com/civitas/configadapter/configuration/
│       └── AppConfig.java                     # Implementation of ApplicationConfig
├── event-handler-kafka/
│   ├── pom.xml
│   └── src/main/java/com/civitas/event/handler/kafka/
│       ├── CloudEventProcessor.java           # CloudEvent deserializer
│       └── KafkaEventHandler.java
├── config-adapter-application/
│   ├── pom.xml
│   └── src/main/java/com/civitas/configadapter/application/
│       └── Application.java
├── config-adapter-keycloak/
│   ├── pom.xml
│   └── src/main/java/com/civitas/configadapter/keycloak/
│       └── KeycloakAdapter.java
└── config-adapter-examples/
    ├── pom.xml
    └── src/main/java/com/civitas/configadapter/examples/
        └── DummyLogAdapter.java
```

### Dependencies Between Modules

```
config-adapter-configuration (AppConfig, environment variable support)
    ↑
config-adapter-api (interfaces, models, depends on configuration)
    ↑
    ├── event-handler-kafka (KafkaEventHandler accepts ConfigAdapter in constructor)
    ├── config-adapter-keycloak (KeycloakAdapter for production)
    ├── config-adapter-examples (DummyLogAdapter for reference)
    ↑
    └── config-adapter-application (Application runner - injects adapters into handlers)
```

## Benefits of This Architecture

1. **Modularity**: Each module has a single responsibility
2. **Reusability**: Use API module with any message broker or backend service
3. **Testability**: Each module can be tested independently
4. **Extensibility**: Easy to add new brokers or adapters without changing existing code
5. **Clear Contracts**: Interfaces define clear boundaries between layers
6. **Efficiency**: Kafka-level topic filtering (not post-consumption filtering)
7. **Independence**: Each adapter runs independently with its own consumer
8. **Scalability**: Multiple adapters can run in the same JVM without interference

## Examples

### KeycloakAdapter (config-adapter-keycloak)
Full production implementation that:
- Subscribes to 13 topics covering user, realm, and client operations (using Topics constants)
- Consumes CloudEvents from Kafka via dedicated handler
- Processes create/update/delete operations for realms, clients, and users
- Applies changes to Keycloak via REST API
- Publishes result events back to Kafka with correlation tracking

### DummyLogAdapter (config-adapter-examples)
Simple reference implementation that:
- Subscribes to 3 user topics (created, updated, deleted)
- Demonstrates minimal adapter implementation
- Just logs received events with formatted output
- Useful for testing, debugging, and as a learning reference

Both adapters can run simultaneously, each with their own Kafka handler subscribed to their specific topics.

## License

European Union Public License License (EU-PL) 1.2
