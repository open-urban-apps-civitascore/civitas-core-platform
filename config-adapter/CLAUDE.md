# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Full build with unit tests
mvn clean install

# Unit tests only (*Test.java, via surefire)
mvn test

# Parallel test execution (2 modules at once, ~30% faster)
mvn test -T 2

# Unit + integration tests (*IT.java, requires Docker for Testcontainers)
mvn verify

# Single module
mvn test -pl config-adapter-frost

# Single test class
mvn test -pl config-adapter-frost -Dtest=FrostAdapterTest

# Single test method
mvn test -pl config-adapter-frost -Dtest=FrostAdapterTest#shouldHandleCreateProject

# Code formatting (Google Java Format via Spotless)
mvn spotless:apply

# Check formatting (runs automatically during verify)
mvn spotless:check

# Build fat JAR (with all adapter plugins)
mvn package -Pdist -pl config-adapter-application
```

## Architecture

Java 21 Maven multi-module project. Event-driven config adapter framework consuming CNCF CloudEvents from Kafka, applying changes to backend services (Keycloak, APISIX, RedPanda Connect, FROST), and publishing result events.

### Module Dependency Graph

```
config-adapter-api          ← Pure interfaces & models, no impl dependencies
    ↑
    ├── config-adapter-configuration  ← AppConfig (Commons Configuration2), env var support
    ├── event-handler-kafka           ← KafkaEventHandler (consumer + publisher), virtual threads
    ├── config-adapter-keycloak       ← Keycloak Admin Client REST adapter
    ├── config-adapter-apisix         ← APISIX Admin API adapter
    ├── config-adapter-redpanda       ← RedPanda Connect Streams API adapter (JAX-RS/Jersey)
    ├── config-adapter-frost          ← FROST SensorThings API adapter (JAX-RS/Jersey)
    └── config-adapter-examples       ← DummyLogAdapter (logging reference impl)
    ↑
config-adapter-application  ← Bootstrap, ServiceLoader discovery, health checks, shade JAR
```

### Core Pattern: Template Method + ServiceLoader

Adapters extend `AbstractConfigAdapter` and implement `doProcessConfigEvent()`. The base class handles error wrapping and failure result publishing. All modules are discovered at runtime via `java.util.ServiceLoader` — registration files in `META-INF/services/`.

Key interfaces: `ConfigAdapter`, `EventConsumer`, `EventPublisher`, `AdapterConfig` (property access), `ApplicationConfig` (extends AdapterConfig with app-level config).

### Event Flow

1. `KafkaEventHandler` consumes CloudEvents → `CloudEventProcessor` deserializes to `ConfigEvent`
2. `AbstractConfigAdapter.processConfigEvent()` delegates to `doProcessConfigEvent()`
3. Adapter publishes `ConfigResultEvent` (SUCCESS/FAILURE) to the `resultTopic` from metadata
4. Retry: `RetryableAdapterException` → exponential backoff; `FatalAdapterException` → DLQ immediately

### Config Value Hierarchy (Sealed)

```
IdmConfigValue (sealed) → UserConfig, ClientConfig, RealmConfig, RoleConfig, GroupConfig
ApisixConfigValue → RouteConfigValue
FrostConfigValue (interface with toApiMap())
```

### Error Codes

`AdapterErrorCode` enum: 1xxx = fatal/validation, 2xxx = retryable/connectivity, 3xxx = adapter-specific, 9xxx = unknown. Each code carries retryable flag, internal log template, and safe external message.

## Conventions

- **Formatting**: Google Java Format enforced by Spotless; run `mvn spotless:apply` before committing
- **License**: EUPL-1.2 header auto-inserted on all Java files by Spotless
- **Logging**: All user-controlled strings in log statements use `Encode.forJava(...)` (OWASP Encoder)
- **Imports**: Always use Java imports, never fully qualified class names
- **src-gen\* folders**: Generated code — do not edit
- **Adapters depend only on `AdapterConfig` interface**, never on `AppConfig` directly
- **config-adapter-application** is the only module with compile dependency on config-adapter-configuration

## Testing

- Unit tests: JUnit 5 + Mockito. Pattern: mock `AdapterConfig`, create adapter, verify behavior with `ArgumentCaptor`
- Integration tests: Testcontainers with `confluentinc/cp-kafka:7.5.3`, Awaitility for async assertions
- FROST tests require JVM arg: `--add-opens java.base/java.net=ALL-UNNAMED` (configured in its pom.xml surefire plugin)
- Test config uses `AppConfig(new MapConfiguration(props))` with inline properties

## Local Development

`docker-compose.yml` provides Zookeeper, Kafka (ports 9092/29092), PostgreSQL (port 5433), and Keycloak (port 8080, admin/admin).

```bash
docker compose up -d
```

Runtime config: `config-adapter-application/src/main/resources/application.properties`. All properties overridable via env vars (dots→underscores, uppercase: `kafka.bootstrap.servers` → `KAFKA_BOOTSTRAP_SERVERS`).
