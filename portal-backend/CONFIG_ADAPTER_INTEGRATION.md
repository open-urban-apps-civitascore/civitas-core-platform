# Event Publishing for Config Events

This document describes how the portal-backend publishes configuration events to messaging systems (Kafka, RabbitMQ, etc.) for consumption by the Config Adapter system.

## Overview

When users, groups, or roles are created/updated/deleted in the portal-backend, configuration events are automatically published to messaging topics/queues. These events are consumed by the Config Adapter, which then applies the changes to the target systems (e.g., Keycloak IDM).

The event publishing system is **pluggable** - you can use Kafka, RabbitMQ, or implement your own publisher.

## Architecture

The system supports two integration patterns:

### 1. Synchronous Validation Pattern (Recommended for CRUD Operations)

```
Portal Backend (User CRUD)
    ↓
EventPublishingService (BaseService extension)
    ├─ create() / update() / delete()
    └─ preValidateWithExternalSystem()
        ↓
ConfigEventPublisherService
    ↓
ConfigEvent → CloudEvent
    ↓
CloudEventPublisher (interface)
    ├─ KafkaCloudEventPublisher + KafkaConfigResultListener
    ├─ RabbitMQCloudEventPublisher
    └─ ... (custom implementations)
    ↓
Messaging System (Kafka/RabbitMQ/etc.)
    ↓
Config Adapter
    ↓
Results back to portal-backend
    ↓
Transaction commits or rolls back based on validation
```

**Key Features:**
- CRUD operations wait for Config Adapter validation
- Automatic transaction rollback if validation fails
- External IDs updated from Config Adapter responses
- Pluggable messaging backends

### 2. Async Fire-and-Forget Pattern

```
Portal Backend (Custom Event)
    ↓
ConfigEventPublisherService
    ↓
ConfigEvent → CloudEvent
    ↓
CloudEventPublisher (interface)
    ↓
Messaging System (Kafka/RabbitMQ/etc.)
    ↓
Config Adapter (async processing)
```

**Key Features:**
- Non-blocking event publishing
- Fast response times
- Optional result handling via CompletableFuture

## Pluggable Publisher Architecture

The system uses a `CloudEventPublisher` interface that allows different messaging implementations:

- **KafkaCloudEventPublisher** - Publishes to Apache Kafka (included)
- **RabbitMQCloudEventPublisher** - Publishes to RabbitMQ (example included)
- **Custom implementations** - Implement `CloudEventPublisher` interface for other systems

## Configuration

### Enable Kafka Publishing

Edit `src/main/resources/application-local.yaml`:

```yaml
keycloak:
  realm: "civitas-core"           # OAuth2 authentication realm
  auth-server-url: http://localhost:8080
  target-realm: "civitas-core"    # Target realm for user/group/role management

kafka:
  enabled: true
  bootstrap-servers: localhost:9092
  acks: all
  retries: 3
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `keycloak.target-realm` | *(required)* | Target Keycloak realm for user/group/role management via Config Adapter |
| `kafka.enabled` | `false` | Enable/disable Kafka publishing |
| `kafka.bootstrap-servers` | `localhost:9092` | Kafka broker addresses |
| `kafka.acks` | `all` | Producer acknowledgment level |
| `kafka.retries` | `3` | Number of retries for failed sends |

**Note:** The `keycloak.target-realm` can be different from `keycloak.realm` (OAuth2 auth realm). This allows managing users in a different realm than the one used for authentication.

## Published Topics

### User Events
- `de.civitascore.idm.user.created` - User creation
- `de.civitascore.idm.user.updated` - User updates
- `de.civitascore.idm.user.deleted` - User deletion

### Group Events
- `de.civitascore.idm.group.created` - Group creation
- `de.civitascore.idm.group.updated` - Group updates
- `de.civitascore.idm.group.deleted` - Group deletion

### Role Events
- `de.civitascore.idm.role.created` - Role creation
- `de.civitascore.idm.role.updated` - Role updates
- `de.civitascore.idm.role.deleted` - Role deletion

## Event Format

Events are published as CloudEvents with the following structure:

### CloudEvent Envelope

```json
{
  "specversion": "1.0",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "source": "urn:civitas:portal-backend",
  "type": "de.civitascore.config.event",
  "datacontenttype": "application/json",
  "topic": "de.civitascore.idm.user.created",
  "operation": "CREATE",
  "targetcomponent": "keycloak",
  "targetresource": "civitas-core",
  "data": { ... }
}
```

### ConfigEvent Data

```json
{
  "metadata": {
    "messageId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2025-12-11T15:30:00Z",
    "source": "portal-backend",
    "correlationId": "abc123-def456",
    "configVersion": "1.0",
    "resultTopic": null
  },
  "payload": {
    "targetComponent": "user",
    "targetResource": "civitas-core",
    "operation": "CREATE",
    "config": {
      "path": "/users",
      "value": {
        "id": null,
        "username": "john.doe@example.com",
        "email": "john.doe@example.com",
        "firstName": "John",
        "lastName": "Doe",
        "enabled": true,
        "emailVerified": false,
        "groups": ["admins", "developers"]
      }
    }
  }
}
```

**Semantic Field Usage:**
- `targetComponent`: Resource type (`"user"`, `"role"`, `"client"`, `"realm"`)
- `targetResource`: Realm name (e.g., `"civitas-core"`) - NOT a path
- `config.value.id`: Resource identifier for UPDATE/DELETE operations
  - **Users/Clients**: Keycloak UUID (e.g., `"06702f15-1439-4958-b069-2ac5716c7a5c"`)
  - **Roles**: Role name (e.g., `"admin"`)
  - **Realms**: Realm name (e.g., `"civitas-core"`)
  - `null` for CREATE operations (assigned by Keycloak)

## Local Development Setup

### 1. Start Kafka

Using Docker Compose:

```bash
cd ../dev-environment/kafka
docker-compose up -d
```

### 2. Enable Kafka in Application

Set `kafka.enabled=true` in `application-local.yaml`

### 3. Start Portal Backend

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres
```

### 4. Verify Events

Monitor Kafka topics:

```bash
# List topics
docker exec -it kafka kafka-topics --list \
  --bootstrap-server localhost:9092

# Consume user creation events
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic de.civitascore.idm.user.created \
  --from-beginning
```

## Testing

### Without Kafka (Logging Only)

When `kafka.enabled=false` (default), events are logged but not published:

```bash
mvn test
```

Log output:
```
INFO ConfigEventPublisherService - Kafka not configured - ConfigEvent logged only: messageId=...
```

### Integration Testing with Config Adapter

The recommended approach for integration testing is to run the Config Adapter in-process using `ConfigAdapterTestHelper`. This provides true end-to-end testing with actual Keycloak operations.

#### Test Configuration

**1. Test Profile (`application-test-integration.yml`)**

```yaml
# Kafka disabled by default for tests without @EmbeddedKafka
kafka:
  enabled: false
  bootstrap-servers: ${spring.embedded.kafka.brokers:localhost:9092}
  # Default placeholder prevents "Could not resolve placeholder" errors
```

**2. Enable Kafka for Specific Tests**

```java
@DisplayName("Event Publishing Integration Tests")
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"},
    topics = {
      "de.civitascore.idm.user.created",
      "de.civitascore.idm.user.updated",
      "de.civitascore.idm.user.deleted",
      "de.civitascore.config.results"
    })
@TestPropertySource(properties = {"kafka.enabled=true"})  // ← Enable Kafka for this test
@Import(ConfigAdapterTestConfiguration.class)             // ← In-process adapter as a context bean
class EventPublishingIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @Test
  void shouldCreateUserInKeycloakWhenCreatingUser() {
    // Given
    UserInputDTO input = createValidUserInput();

    // When
    User createdUser = userService.create(input);

    // Then - verify in database
    assertThat(createdUser.getExternalId()).isNotNull();
    assertThat(createdUser.getExternalId()).matches("[0-9a-f-]{36}");

    // Verify in Keycloak
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        UserRepresentation keycloakUser = findKeycloakUserByEmail(input.getEmail());
        assertThat(keycloakUser).isNotNull();
        assertThat(keycloakUser.getId()).isEqualTo(createdUser.getExternalId());
      });
  }
}
```

**Key Benefits:**
- ✅ True end-to-end testing with actual Keycloak
- ✅ Validates CREATE → stores UUID → UPDATE/DELETE with UUID
- ✅ Tests both portal-backend and config-adapter interaction
- ✅ No mocking required
- ✅ Tests run in isolation with @EmbeddedKafka

The adapter is a context bean rather than something built in `@BeforeEach`, so its Kafka consumer is
subscribed during context refresh. The `init` profile initializers publish IDM events from
`ApplicationReadyEvent`; a consumer created per test method would miss those and every publish would
run into its full result timeout.

#### Running Integration Tests

```bash
# Run all integration tests
mvn verify

# Run specific test
mvn verify -Dtest=EventPublishingIntegrationTest
```

## Code Examples

### Pattern 1: Synchronous Validation with EventPublishingService

This pattern automatically integrates Config Adapter validation into CRUD operations:

```java
@Service
public class UserService extends EventPublishingService<User, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final String targetRealm;

  public UserService(
      ConfigEventPublisherService configEventPublisher,
      UserRepository userRepository,
      UserMapper userMapper,
      @Value("${keycloak.target-realm}") String targetRealm) {
    super(configEventPublisher);
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.targetRealm = targetRealm;
  }

  // Implement required abstract methods
  @Override
  protected Topics resolveTopic(String operation) {
    return switch (operation.toLowerCase()) {
      case "create" -> Topics.USER_CREATED;
      case "update" -> Topics.USER_UPDATED;
      case "delete" -> Topics.USER_DELETED;
      default -> throw new IllegalArgumentException("Unknown operation: " + operation);
    };
  }

  @Override
  protected String getTargetComponent() {
    return "user";
  }

  @Override
  protected String getRealm(User entity) {
    return targetRealm;  // Injected from configuration
  }

  @Override
  protected String getConfigPath() {
    return "/users";
  }

  @Override
  protected ConfigValue toConfigValue(User entity) {
    UserConfig config = new UserConfig();

    // Set Keycloak user ID if it exists (required for UPDATE/DELETE operations)
    if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
      config.setId(entity.getExternalId());
    }

    config.setUsername(entity.getEmail());
    config.setEmail(entity.getEmail());
    config.setFirstName(entity.getFirstName());
    config.setLastName(entity.getLastName());
    config.setEnabled(true);
    config.setEmailVerified(false);

    // Map groups
    if (entity.getGroups() != null && !entity.getGroups().isEmpty()) {
      config.setGroups(entity.getGroups().stream()
        .map(Group::getName)
        .toList());
    }

    return config;
  }

  @Override
  protected void updateExternalId(User entity, String externalId) {
    if (externalId != null && !externalId.isBlank()) {
      entity.setExternalId(externalId);
    }
  }
}
```

**Benefits:**
- Automatic validation during create/update/delete
- Transaction rollback if Config Adapter rejects
- External ID automatically updated from response
- No manual event publishing code needed

**Error Handling:**
- `ExternalSystemRejectionException` thrown if Config Adapter rejects
- `ExternalSystemTimeoutException` thrown if Config Adapter doesn't respond
- Both exceptions trigger transaction rollback

**Important: Mapper Configuration for External ID**

When using MapStruct for entity mapping, ensure the `externalId` field is ignored during updates to preserve the Keycloak UUID:

```java
@Mapper(componentModel = "spring")
public interface UserMapper extends DtoMapper<UserInputDTO, UserOutputDTO, User> {

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
  @Mapping(target = "groups", ignore = true)
  @Mapping(target = "externalId", ignore = true)  // ← CRITICAL: Preserve external ID
  @Override
  void updateEntity(@MappingTarget User entity, UserInputDTO input);
}
```

Without this configuration, the mapper will set `externalId` to `null` during updates, causing UPDATE/DELETE operations to fail.

### Pattern 2: Async Fire-and-Forget with ConfigEventPublisherService

Use this pattern for custom events or when you don't need immediate validation:

```java
@Service
public class MyCustomService {

  private final ConfigEventPublisherService publisher;

  public MyCustomService(ConfigEventPublisherService publisher) {
    this.publisher = publisher;
  }

  public void doSomething() {
    MyConfigValue configValue = new MyConfigValue();
    // populate config value...

    // Option A: Fire-and-forget (async, no waiting)
    publisher.publishConfigEvent(
      Topics.CUSTOM_TOPIC,
      "target-component",
      "target-resource",
      Operation.CREATE,
      "/path/to/config",
      configValue
    );

    // Option B: Wait for result if needed
    CompletableFuture<ConfigResultEvent> future = publisher.publishConfigEvent(
      Topics.CUSTOM_TOPIC,
      "target-component",
      "target-resource",
      Operation.CREATE,
      "/path/to/config",
      configValue
    );

    // Handle result asynchronously
    future.thenAccept(result -> {
      if (result.status() == ConfigResultEvent.Status.SUCCESS) {
        log.info("Config Adapter processed successfully: {}", result.resourceId());
      } else {
        log.error("Config Adapter failed: {} - {}", result.errorCode(), result.message());
      }
    }).exceptionally(ex -> {
      log.error("Config Adapter error: {}", ex.getMessage());
      return null;
    });
  }

  // Or use convenience methods for common operations
  public void createUser(UserConfig userConfig) {
    publisher.publishUserCreated("civitas-core", userConfig);
  }
}
```

**Benefits:**
- Non-blocking, fast execution
- Can handle results asynchronously if needed
- Doesn't block transactions
- Good for notifications and audit trails

## Troubleshooting

### Events Not Published

1. Check Kafka is enabled: `kafka.enabled=true`
2. Verify Kafka is running: `docker ps | grep kafka`
3. Check application logs for connection errors
4. Verify bootstrap-servers configuration

### Connection Errors

```
Failed to publish ConfigEvent to Kafka: Connection refused
```

**Solution**: Ensure Kafka is running and accessible at the configured bootstrap-servers address.

### Serialization Errors

```
Failed to send CloudEvent to Kafka: SerializationException
```

**Solution**: Verify CloudEvent data is properly formatted JSON.

### UPDATE/DELETE Operations Fail with "templateValues entry was null"

```
ExternalSystemRejectionException: Config Adapter processing failed:
RESTEASY004645: templateValues entry was null (errorCode=USER_UPDATE_FAILED)
```

**Root Cause**: The `externalId` field is `null` when sending UPDATE/DELETE events. This happens when:
1. MapStruct mapper doesn't ignore `externalId` during updates
2. The `toConfigValue()` method doesn't set the `id` field from `externalId`

**Solution**:

1. **Ensure mapper ignores `externalId`:**
```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
@Mapping(target = "externalId", ignore = true)  // ← MUST ignore
@Override
void updateEntity(@MappingTarget User entity, UserInputDTO input);
```

2. **Ensure `toConfigValue()` sets the ID:**
```java
@Override
protected ConfigValue toConfigValue(User entity) {
  UserConfig config = new UserConfig();

  // Set Keycloak user ID for UPDATE/DELETE operations
  if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
    config.setId(entity.getExternalId());
  }

  // ... rest of configuration
  return config;
}
```

### Test Placeholder Resolution Error

```
PlaceholderResolutionException: Could not resolve placeholder
'spring.embedded.kafka.brokers' in value "${spring.embedded.kafka.brokers}"
```

**Root Cause**: Test configuration tries to resolve embedded Kafka brokers but `@EmbeddedKafka` annotation is not present.

**Solution**: Use default value in test configuration:

```yaml
kafka:
  enabled: false  # Disabled by default
  bootstrap-servers: ${spring.embedded.kafka.brokers:localhost:9092}
```

Then enable Kafka only in tests that need it:

```java
@EmbeddedKafka(...)
@TestPropertySource(properties = {"kafka.enabled=true"})
class MyKafkaIntegrationTest {
  // Test code
}
```

## Monitoring

### Application Logs

The service logs key events at different levels:

- `INFO` - Event publishing initiated and completed
- `DEBUG` - CloudEvent details and Kafka metadata
- `ERROR` - Publishing failures and exceptions

Example:
```
INFO  ConfigEventPublisherService - Publishing ConfigEvent: topic=de.civitascore.idm.user.created, operation=CREATE, targetComponent=keycloak, targetResource=civitas-core, messageId=abc-123
INFO  ConfigEventPublisherService - Successfully published ConfigEvent to Kafka: messageId=abc-123, topic=de.civitascore.idm.user.created
DEBUG ConfigEventPublisherService - CloudEvent sent to Kafka successfully: messageId=abc-123, topic=de.civitascore.idm.user.created, partition=0, offset=42
```

### Kafka Metrics

Monitor producer metrics using Kafka JMX or external monitoring tools.

## Dependencies

The Kafka integration requires:

```xml
<!-- Config adapter API -->
<dependency>
    <groupId>de.civitas-core</groupId>
    <artifactId>config-adapter-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Kafka event handler with CloudEvents support -->
<dependency>
    <groupId>de.civitas-core</groupId>
    <artifactId>event-handler-kafka</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Implementing Custom Publishers

You can implement your own CloudEvent publisher for any messaging system by implementing the `CloudEventPublisher` interface:

### 1. Implement the Interface

```java
package de.civitascore.portal.messaging.custom;

import de.civitascore.portal.messaging.CloudEventPublisher;
import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyCustomPublisher implements CloudEventPublisher {

  private final MyMessagingClient client;

  public MyCustomPublisher(MyMessagingClient client) {
    this.client = client;
  }

  @Override
  public void publish(String topic, String messageId, CloudEvent cloudEvent)
      throws PublishException {
    try {
      // Your publishing logic here
      client.send(topic, cloudEvent);
      log.info("Published CloudEvent to custom system: messageId={}", messageId);
    } catch (Exception e) {
      throw new PublishException("Failed to publish to custom system", e);
    }
  }

  @Override
  public String getName() {
    return "my-custom-publisher";
  }

  @Override
  public boolean isReady() {
    return client.isConnected();
  }
}
```

### 2. Create a Configuration Class

```java
@Configuration
public class CustomPublisherConfig {

  @Bean
  @ConditionalOnProperty(name = "custom.enabled", havingValue = "true")
  public CloudEventPublisher myCustomPublisher(CustomConfigProperties properties) {
    MyMessagingClient client = new MyMessagingClient(properties.getUrl());
    return new MyCustomPublisher(client);
  }

  @Bean
  @ConfigurationProperties(prefix = "custom")
  public CustomConfigProperties customConfigProperties() {
    return new CustomConfigProperties();
  }
}
```

### 3. Configure in application.yaml

```yaml
custom:
  enabled: true
  url: http://my-messaging-system:8080
```

The `ConfigEventPublisherService` will automatically use your custom publisher!

## Switching Between Publishers

You can switch between different publishers by configuration:

```yaml
# Use Kafka
kafka:
  enabled: true
rabbitmq:
  enabled: false

# OR use RabbitMQ
kafka:
  enabled: false
rabbitmq:
  enabled: true
```

**Note:** Only one publisher should be enabled at a time. If multiple publishers are configured, Spring will inject the first one found.

## RabbitMQ Example

An example RabbitMQ publisher implementation is included in the codebase:

1. **Implementation**: `messaging/rabbitmq/RabbitMQCloudEventPublisher.java`
2. **Configuration**: `config/RabbitMQEventPublisherConfig.java.example`

To use RabbitMQ:

1. Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

2. Rename the example config file (remove `.example`)
3. Add RabbitMQ configuration to `application-local.yaml`
4. Uncomment the code in both files

## Deprecation Notices

The following classes are deprecated and scheduled for removal:

### Deprecated Classes

| Class | Replacement | Reason |
|-------|-------------|--------|
| `SynchronousEventPublisher` | `ConfigEventPublisherService` + `CloudEventPublisher` | New approach provides pluggable backends, CloudEvents support, and better config-adapter integration |
| `ConfigAdapterResultListener` | `KafkaConfigResultListener` | Works with new CloudEventPublisher architecture |
| `TopicResolver` | `Topics` enum from config-adapter-api | Standardized topic names across all services |

### Migration Guide

If you have custom services using the old approach:

**Before (Deprecated):**
```java
public class MyService extends EventPublishingService<MyEntity, MyDTO> {
  public MyService(SynchronousEventPublisher syncPublisher, TopicResolver resolver, ...) {
    super(syncPublisher, resolver);
  }

  protected String getAggregateType() { return "MyEntity"; }
  protected Object toKafkaRepresentation(MyEntity entity) { return new MyEventDTO(...); }
}
```

**After (Current):**
```java
public class MyService extends EventPublishingService<MyEntity, MyDTO> {
  public MyService(ConfigEventPublisherService configPublisher, ...) {
    super(configPublisher);
  }

  protected Topics resolveTopic(String operation) {
    return switch (operation) {
      case "create" -> Topics.MY_ENTITY_CREATED;
      case "update" -> Topics.MY_ENTITY_UPDATED;
      case "delete" -> Topics.MY_ENTITY_DELETED;
    };
  }

  protected String getTargetComponent() { return "my-component"; }
  protected String getRealm(MyEntity entity) { return "my-realm"; }
  protected String getConfigPath() { return "/my-entities"; }
  protected ConfigValue toConfigValue(MyEntity entity) { return buildConfigValue(entity); }
}
```

## Further Reading

- [Config Adapter Documentation](../config-adapter/README.md)
- [CloudEvents Specification](https://cloudevents.io/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
