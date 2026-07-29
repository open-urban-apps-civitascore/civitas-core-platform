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

Java 25 Maven multi-module project. Event-driven config adapter framework consuming CNCF CloudEvents from Kafka, applying changes to backend services (Keycloak, APISIX, Apache NiFi, FROST, GeoServer, PostGIS), and publishing result events.

### Module Dependency Graph

```
config-adapter-api          ← Pure interfaces & models, no impl dependencies
    ↑
    ├── config-adapter-configuration  ← AppConfig (Commons Configuration2), env var support
    ├── event-handler-kafka           ← KafkaEventHandler (consumer + publisher), virtual threads
    ├── config-adapter-keycloak       ← Keycloak Admin Client REST adapter
    ├── config-adapter-apisix         ← APISIX Admin API adapter
    ├── config-adapter-nifi           ← Apache NiFi pipeline adapter (transforms the engine-neutral graph → curated NiFi flow, deploys via REST)
    ├── config-adapter-frost          ← FROST SensorThings API adapter (JAX-RS/Jersey)
    ├── config-adapter-geoserver      ← GeoServer REST API adapter (JAX-RS/Jersey)
    ├── config-adapter-examples       ← DummyLogAdapter (logging reference impl)
    ├── config-adapter-postgis        ← PostgreSQL/PostGIS DDL adapter (tables, schemas, roles+grants; JDBC + HikariCP) + PostgisSagaHandler
    └── config-adapter-flowable       ← Flowable saga engine (embedded, PostgreSQL state; BPMN 2.0 process definitions). Sole saga orchestrator — the legacy custom config-adapter-orchestrator has been removed.
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
IdmConfigValue (sealed)     → UserConfig, ClientConfig, RealmConfig, RoleConfig, GroupConfig
ApisixConfigValue           → RouteConfigValue
FrostConfigValue            (class extending AbstractApiModel with toApiMap())
GeoServerConfigValue (passthrough container; toApiMap() returns additionalProperties as-is)
PostgisConfigValue (sealed) → TableConfig, SchemaConfig, DbRoleConfig
```

### Error Codes

`AdapterErrorCode` enum: 1xxx = fatal/validation, 2xxx = retryable/connectivity, 3xxx = adapter-specific (30xx Keycloak, 31xx APISIX, 32xx FROST, 34xx GeoServer, 35xx PostGIS, 36xx NiFi), 9xxx = unknown. Each code carries retryable flag, internal log template, and safe external message.

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
- Naming decides the runner: `*Test.java` → Surefire (`mvn test`, Docker-free), `*IT.java` → Failsafe (`mvn verify`, Testcontainers). Any container-based test MUST be named `*IT`, otherwise `mvn test` boots containers.
- Failsafe is bound once in the parent pom; modules only add `<configuration>`, never the goal binding
- FROST/GeoServer ITs require JVM arg `--add-opens java.base/java.net=ALL-UNNAMED` (Jersey PATCH via reflection); APISIX ITs require `-Djdk.httpclient.allowRestrictedHeaders=host`. Both are set on the failsafe plugin in the respective pom.xml, prefixed with `@{argLine}` so jacoco's agent arg survives.
- The parent pom defines an empty `argLine` property so `@{argLine}` still resolves when surefire/failsafe goals are invoked directly from the CLI (as CI does), without jacoco's prepare-agent
- Test config uses `AppConfig(new MapConfiguration(props))` with inline properties
- Awaitility's `pollDelay` defaults to `pollInterval`, so every `await()` blocks one full interval before the first check. Any test class using Awaitility MUST zero it in a static initializer: `Awaitility.setDefaultPollDelay(Duration.ZERO)`. There is no shared registration point (no Spring, and `junit-platform-launcher` is not on the test classpath), so it goes in each abstract IT base class and in each standalone test class that awaits
- Container readiness must be awaited once after startup (static block), never in `@BeforeEach` — the containers are `static` and shared per JVM, so a per-method readiness poll is pure dead time
- `Thread.sleep` is only legitimate for a *negative* assertion (proving something never happens, or state holding across ticks); such dwells carry a comment saying so. Anything waiting for a condition to become true uses Awaitility

## Local Development

`docker-compose.yml` provides Zookeeper, Kafka (ports 9092/29092), PostgreSQL (port 5433), and Keycloak (port 8080, admin/admin).

```bash
docker compose up -d
```

Runtime config: `config-adapter-application/src/main/resources/application.properties`. All properties overridable via env vars (dots→underscores, uppercase: `kafka.bootstrap.servers` → `KAFKA_BOOTSTRAP_SERVERS`).
