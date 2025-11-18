# Civitas Config Adapter

Modular configuration adapter framework that consumes CloudEvents and applies configuration changes to various backend services.

## Architecture

The project is split into four independent modules for maximum reusability:

```
┌─────────────────────┐
│ config-adapter-api  │  Core interfaces and models
└──────────┬──────────┘
           │
    ┌──────┴──────────┬──────────────────┐
    │                 │                  │              
┌───▼────────────┐  ┌─▼──────────────┐ ┌─▼────────────┐ 
│ event-handler- │  │ event-handler- │ │config-adapter│ 
│ kafka          │  │ other-broker   │ │ application  │ 
└────────────────┘  └────────────────┘ └──────┬───────┘ 
                                              │
                    ┌─────────────────────────┴─────────────────┐
                    │                                           │
         ┌──────────▼────────────────┐             ┌────────────▼──────────┐
         │ config-adapter-keycloak   │             │ Other implementations │
         │ Keycloak                  │             │                       │
         └───────────────────────────┘             └───────────────────────┘
```

## Modules

### 1. config-adapter-api
Core interfaces and models that define the adapter contract.

**Key Components:**
- `ConfigAdapter` - Interface for backend service adapters
- `EventConsumer` - Interface for message consumers
- `EventPublisher` - Interface for publishing result events
- `CloudEventProcessor` - Internal helper for CloudEvent deserialization
- `ConfigEvent` - Event data model with metadata and payload

**Usage:** Depend on this module to create new adapters or consumers.

### 2. event-handler-kafka
Kafka-specific message consumer / publisher implementation.

**Key Components:**
- `KafkaEventHandler` - Consumes CloudEvents from Kafka and implements EventPublisher

**Usage:** Use this module to consume events from Apache Kafka.

### 3. config-adapter-application
Generic application runner that uses reflection to load adapters and consumers.

**Key Components:**
- `Application` - Main entry point that reads configuration and wires components
- `AppConfig` - Base configuration class with properties loading

**Features:**
- Supports single or multiple adapters configuration
- Each adapter is injected into its own EventConsumer
- Consumer manages complete lifecycle (adapter + consumer)
- Adapters declare which Kafka topics they want to subscribe to via `getSubscribedTopics()`
- Simplified architecture with cleaner lifecycle management
- Automatic resource cleanup on shutdown

**Usage:** Configure adapters via application.properties using comma-separated list.

### 4. config-adapter-keycloak
Production implementation of Keycloak adapter.

**Key Components:**
- `KeycloakAdapter` - Manages Keycloak configuration via REST API

**Usage:** Production-ready adapter that integrates with Keycloak for user, realm, and client management.

### 5. config-adapter-examples
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

**Multiple Adapters (comma-separated)**
```properties
adapters=com.civitas.configadapter.keycloak.KeycloakAdapter,com.civitas.configadapter.examples.DummyLogAdapter
eventhandler.class=com.civitas.event.handler.kafka.KafkaEventHandler
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
# Multiple adapters - each will get its own handler
adapters=com.civitas.configadapter.keycloak.KeycloakAdapter,com.civitas.configadapter.examples.DummyLogAdapter

# Kafka event handler class
eventhandler.class=com.civitas.event.handler.kafka.KafkaEventHandler

# Kafka settings
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Keycloak settings
keycloak.url=http://localhost:8080
keycloak.realm=master
keycloak.username=admin
keycloak.password=admin
keycloak.client.id=admin-cli

# DummyLogAdapter needs no additional configuration
# It subscribes to: user.created, user.updated, user.deleted
# And simply logs all received events
```

### Topic Subscription Example

```java
public class MyAdapter implements ConfigAdapter {

    private static final List<String> SUBSCRIBED_TOPICS = List.of(
            Topics.USER_CREATED,
            Topics.USER_UPDATED,
            Topics.USER_DELETED
    );

    @Override
    public List<String> getSubscribedTopics() {
        return SUBSCRIBED_TOPICS;
    }

    @Override
    public void processConfigEvent(String topic, ConfigEvent event) {
        // Access event data via event.payload() and event.metadata()
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        // Store publisher to send result events
    }
}
```

## Creating a New Adapter

### 1. Add Dependencies

```xml
<dependency>
    <groupId>com.civitas</groupId>
    <artifactId>config-adapter-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Implement ConfigAdapter

```java
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Topics;

import java.util.List;

public class MyServiceAdapter implements ConfigAdapter {

    private static final List<String> SUBSCRIBED_TOPICS = List.of(
        Topics.USER_CREATED,
        Topics.USER_UPDATED,
        Topics.USER_DELETED
        // Add other topics you need
    );

    private final AppConfig config;

    public MyServiceAdapter(AppConfig config) {
        this.config = config;
        // Initialize your service client
    }

    @Override
    public List<String> getSubscribedTopics() {
        return SUBSCRIBED_TOPICS;
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
	    // Store publisher to send result events
	}

    @Override
    public void close() {
        // Cleanup resources
    }
}
```

### 3. Configure application.properties

```properties
# Application Configuration
adapters=com.mycompany.MyServiceAdapter
eventhandler.class=com.civitas.event.handler.kafka.KafkaEventHandler

# Kafka Configuration
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Your Service Configuration
myservice.url=http://localhost:8080
myservice.api.key=your-api-key
```

The generic `Application` class from `config-adapter-application` will automatically:
1. Load your adapter using reflection
2. Inject the adapter into a new `KafkaEventHandler` instance
3. The consumer calls `getSubscribedTopics()` to subscribe to the adapter's topics
4. Start the consumer which manages both its own lifecycle and the adapter's lifecycle

## Creating a New Message Handler

### 1. Implement EventHandler

```java
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.core.CloudEventProcessor;
import com.civitas.configadapter.messaging.EventConsumer;

public class RabbitMQEventConsumer implements EventConsumer {

    private final ConfigAdapter adapter;
    private final CloudEventProcessor processor;
    private final AppConfig config;

    public RabbitMQEventConsumer(AppConfig config, ConfigAdapter adapter) {
        this.config = config;
        this.adapter = adapter;
        this.processor = new CloudEventProcessor(adapter);

        // Initialize RabbitMQ connection and subscribe to adapter.getSubscribedTopics()
    }

    @Override
    public void start() {
        // Start consuming messages from subscribed topics
        // Call processor.handleEvent(topic, cloudEvent) for each message
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

### 2. Configure in application.properties

```properties
# Use your custom consumer
eventhandler.class=com.mycompany.RabbitMQEventConsumer

# Each adapter will get its own RabbitMQEventConsumer instance
adapters=com.civitas.configadapter.keycloak.KeycloakAdapter
```

The Application class will automatically:
1. Instantiate each adapter
2. Inject the adapter into a new consumer instance (via constructor)
3. The consumer manages both its own lifecycle and the adapter's lifecycle

## CloudEvent Format

### Event Type Convention

Events follow the pattern: `core.civitas.idm.{resource}.{action}`

Examples (from `Topics` constants):
- `core.civitas.idm.user.created`
- `core.civitas.idm.user.updated`
- `core.civitas.idm.user.deleted`
- `core.civitas.idm.realm.created`
- `core.civitas.idm.client.updated`

All available topic constants are defined in `com.civitas.configadapter.model.Topics`.

### Event Structure

```json
{
  "specversion": "1.0",
  "type": "core.civitas.idm.user.created",
  "source": "civitas.idm.provisioning",
  "id": "unique-event-id",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-123",
      "timestamp": "2025-01-15T10:00:00Z",
      "source": "idm.service",
      "correlationId": "corr-456",
      "configVersion": "1.0",
      "resultTopic": "idm.results"
    },
    "payload": {
      "targetComponent": "user",
      "targetResource": "realms/my-realm/users/user-789",
      "operation": "CREATE",
      "config": {
        "path": "realms/my-realm/users/user-789",
        "value": {
          "username": "john.doe",
          "email": "john@example.com",
          "enabled": true
        }
      }
    }
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
- `ConfigAdapter` interface with `getSubscribedTopics()` and `setEventPublisher()` methods
- `EventConsumer` interface for message consumers
- `EventPublisher` interface for result publishing
- `ConfigEvent` record with metadata and payload structure
- `Topics` class with centralized topic constants
- `CloudEventProcessor` internal helper for deserialization

No implementation-specific dependencies.

### event-handler-kafka

Kafka-specific implementation:
- Depends on: `config-adapter-api`
- Kafka Clients
- CloudEvents Kafka binding

Contains:
- `KafkaEventHandler` with constructor accepting `(AppConfig, ConfigAdapter)`
- Implements both EventConsumer and EventPublisher interfaces
- Subscribes to multiple Kafka topics at broker level
- Uses virtual threads for efficient concurrent consumption

No backend service dependencies.

### config-adapter-application

Generic application runner:
- Depends on: `config-adapter-api`
- Uses reflection to load configured adapters and consumers
- `AppConfig` class for property management (part of API module)

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

### Kafka Configuration

```properties
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group
```

Note: Individual topics are not configured in properties. Each adapter declares its own topics via `getSubscribedTopics()`.

### Example: Single Adapter Configuration

```properties
# Application settings
adapters=com.civitas.configadapter.keycloak.KeycloakAdapter
eventhandler.class=com.civitas.event.handler.kafka.KafkaEventHandler

# Kafka settings
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Keycloak settings
keycloak.url=http://localhost:8080
keycloak.realm=master
keycloak.username=admin
keycloak.password=admin
keycloak.client.id=admin-cli
```

### Example: Multiple Adapters Configuration

```properties
# Run both KeycloakAdapter and DummyLogAdapter
adapters=com.civitas.configadapter.keycloak.KeycloakAdapter,com.civitas.configadapter.examples.DummyLogAdapter
eventhandler.class=com.civitas.event.handler.kafka.KafkaEventHandler

# Kafka settings
kafka.bootstrap.servers=localhost:9092
kafka.group.id=config-adapter-group

# Keycloak settings (only needed by KeycloakAdapter)
keycloak.url=http://localhost:8080
keycloak.realm=master
keycloak.username=admin
keycloak.password=admin
keycloak.client.id=admin-cli

# DummyLogAdapter needs no additional configuration
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

## Development

### Project Structure

```
civitas-config-adapter/
├── pom.xml                                    # Parent POM
├── config-adapter-api/
│   ├── pom.xml
│   └── src/main/java/com/civitas/configadapter/
│       ├── adapter/
│       │   └── ConfigAdapter.java
│       ├── config/
│       │   └── AppConfig.java
│       ├── messaging/
│       │   ├── EventConsumer.java
│       │   └── EventPublisher.java
│       ├── core/
│       │   └── CloudEventProcessor.java
│       └── model/
│           ├── ConfigEvent.java
│           ├── Metadata.java
│           ├── Payload.java
│           └── Config.java
├── event-handler-kafka/
│   ├── pom.xml
│   └── src/main/java/com/civitas/event/handler/kafka/
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
config-adapter-api (contains Topics constants, ConfigAdapter interface, EventConsumer interface)
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

MIT
