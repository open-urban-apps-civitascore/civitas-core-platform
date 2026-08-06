# CLAUDE.md

Multi-module event-driven config adapter framework for the Civitas Core platform. Java 25, Maven. Consumes CNCF CloudEvents from Kafka, applies the changes to backend services (Keycloak, APISIX, Apache NiFi, FROST, GeoServer, PostGIS), and publishes result events.

> Monorepo-wide conventions, Git workflow and credential handling live in the repository-root `CLAUDE.md`, which is not tracked here.

## Build Commands

```bash
mvn clean install    # full build with unit tests
mvn test             # unit tests only (*Test.java, surefire)
mvn test -T 2        # parallel module build
mvn verify           # + integration tests (*IT.java, needs Docker for Testcontainers)

mvn test -pl config-adapter-frost                   # one module
mvn test -pl <module> -Dtest=<TestClass>            # one class
mvn test -pl <module> -Dtest=<TestClass>#<method>   # one method

mvn spotless:apply   # format (Google Java Format via Spotless)
mvn spotless:check   # check formatting (runs automatically during verify)

mvn package -Pdist -pl config-adapter-application   # fat JAR with all adapter plugins
```

`-Pdist` is required. Without it the JAR carries no adapter plugins and the bootstrap fails with `Adapter not found via ServiceLoader`.

PMD, CPD and SpotBugs are bound per-module at `verify` with `failOnViolation`/`failOnError`. `mvn test` does not run them, so a green `mvn test` is not a green build. Rulesets live in `<module>/pmd-rules.xml` and `<module>/spotbugs-exclude.xml`.

## Architecture

### Modules

`pom.xml` `<modules>` is the authority on which modules exist. The dependency rules that are not evident from a pom:

- Every module depends on `config-adapter-api` alone; adapters never depend on one another.
- `config-adapter-application` depends on all of them, discovers plugins via ServiceLoader, and shades the fat JAR.
- `config-adapter-nifi` is saga-only: it registers a `SagaCommandHandler` and no `ConfigAdapter`, so it is unreachable over the CloudEvent path.
- `config-adapter-flowable` embeds the Flowable engine with PostgreSQL state and orchestrates every saga. BPMN process definitions live under its `src/main/resources/processes/`.

### Core Pattern: Template Method + ServiceLoader

Adapters extend `AbstractConfigAdapter` and implement `doProcessConfigEvent()`. The base class handles error wrapping and failure result publishing.

Two SPIs are discovered at runtime via `java.util.ServiceLoader`, registered independently under `META-INF/services/`. A module may register either or both:

| SPI | Purpose |
|---|---|
| `ConfigAdapter` | Applies a single config event over the CloudEvent path |
| `SagaCommandHandler` | Executes a saga step and its teardown (`SagaCommandMessage` → `SagaCommandResult`) |

Key interfaces: `ConfigAdapter`, `SagaCommandHandler`, `EventConsumer`, `EventPublisher`, `AdapterConfig` (property access), `ApplicationConfig` (extends AdapterConfig with app-level config).

### Event Flow

1. `KafkaEventHandler` consumes CloudEvents on a virtual thread → `CloudEventProcessor` deserializes to `ConfigEvent`
2. `AbstractConfigAdapter.processConfigEvent()` delegates to `doProcessConfigEvent()`
3. Adapter publishes `ConfigResultEvent` (SUCCESS/FAILURE) to the `resultTopic` from metadata
4. Retry: `RetryableAdapterException` → exponential backoff; `FatalAdapterException` → DLQ immediately

### Config Values

Config value types live in `config-adapter-api` under `model/<service>/`. `IdmConfigValue` and `PostgisConfigValue` are `sealed`; their `permits` clauses are the authority on the variants. Two shapes deviate from the sealed pattern: `FrostConfigValue` extends `AbstractApiModel` and serialises through `toApiMap()`, and `GeoServerConfigValue` is a passthrough container whose `toApiMap()` returns `additionalProperties` unchanged.

### Error Codes

`AdapterErrorCode` enum: 1xxx = fatal/validation, 2xxx = retryable/connectivity, 3xxx = adapter-specific (30xx Keycloak, 31xx APISIX, 32xx FROST, 34xx GeoServer, 35xx PostGIS, 36xx NiFi), 9xxx = unknown. Each code carries retryable flag, internal log template, and safe external message.

## Conventions

- **Formatting**: Google Java Format enforced by Spotless; run `mvn spotless:apply` before committing
- **License**: EUPL-1.2 header auto-inserted on all Java files by Spotless
- **Logging**: All user-controlled strings in log statements use `Encode.forJava(...)` (OWASP Encoder)
- **Imports**: Always use Java imports, never fully qualified class names
- **Adapters depend only on `AdapterConfig` interface**, never on `AppConfig` directly
- **config-adapter-application** is the only module with compile dependency on config-adapter-configuration

## Gotchas

- **GeoServer style upsert**: a style POST for a name that already exists returns **403**, not 409, on GeoServer Cloud. The upsert branches on 403 and refreshes the SLD via PUT. A genuine authorisation 403 is indistinguishable from an exists-403 by status code alone, so it falls through to the same PUT and surfaces only if that PUT also fails — inspect the response body rather than trusting the status code.

## Testing

- Unit tests: JUnit 5 + Mockito. Pattern: mock `AdapterConfig`, create adapter, verify behavior with `ArgumentCaptor`
- Integration tests: Testcontainers, Awaitility for async assertions. Container images are pinned centrally in `TestContainerImages` (config-adapter-api test-jar) with Renovate markers; an image MUST be declared there, never inline in a test
- Naming decides the runner: `*Test.java` → Surefire (`mvn test`, Docker-free), `*IT.java` → Failsafe (`mvn verify`, Testcontainers). Any container-based test MUST be named `*IT`, otherwise `mvn test` boots containers.
- Failsafe is bound once in the parent pom; modules only add `<configuration>`, never the goal binding
- FROST/GeoServer ITs require JVM arg `--add-opens java.base/java.net=ALL-UNNAMED` (Jersey PATCH via reflection); APISIX ITs require `-Djdk.httpclient.allowRestrictedHeaders=host`. Both are set on the failsafe plugin in the respective pom.xml, prefixed with `@{argLine}` so jacoco's agent arg survives. `config-adapter-api` needs the same treatment on **surefire**: `--add-opens java.base/javax.crypto=ALL-UNNAMED`, for `MockedStatic` over javax.crypto classes
- The parent pom defines an empty `argLine` property so `@{argLine}` still resolves when surefire/failsafe goals are invoked directly from the CLI (as CI does), without jacoco's prepare-agent
- Test config uses `AppConfig(new MapConfiguration(props))` with inline properties
- Awaitility's `pollDelay` defaults to `pollInterval`, so every `await()` blocks one full interval before the first check. Any test class using Awaitility MUST zero it in a static initializer: `Awaitility.setDefaultPollDelay(Duration.ZERO)`. There is no shared registration point (no Spring, and `junit-platform-launcher` is not on the test classpath), so it goes in each abstract IT base class and in each standalone test class that awaits
- Container readiness must be awaited once after startup (static block), never in `@BeforeEach` — the containers are `static` and shared per JVM, so a per-method readiness poll is pure dead time
- `Thread.sleep` is only legitimate for a *negative* assertion (proving something never happens, or state holding across ticks); such dwells carry a comment saying so. Anything waiting for a condition to become true uses Awaitility

## Local Development

`docker compose up -d` at the module root starts Zookeeper, Kafka, Keycloak and PostgreSQL; `docker-compose.yml` is the authority on ports and image versions. This stack is independent of `dev-environment/`.

`config-adapter-flowable` needs its own `flowable` database and user, created by `docker/postgres/init-flowable.sql` mounted into `/docker-entrypoint-initdb.d/`. PostgreSQL runs init scripts **only when initializing an empty data volume**, so a volume carrying data from any earlier start has no `flowable` database and the adapter crash-loops on startup. `docker compose down -v && docker compose up -d` recreates the volume and re-runs the script.

Runtime config: `config-adapter-application/src/main/resources/application.properties`. Every property is overridable via env var — dots **and** dashes become underscores and the key is uppercased, so `kafka.bootstrap.servers` → `KAFKA_BOOTSTRAP_SERVERS`. An empty env var resolves to the property default and is therefore indistinguishable from an unset one; a property MUST NOT be documented as "leave empty to disable", an explicit sentinel value is required instead.

## Further reading

- [`DEPLOYMENT.md`](DEPLOYMENT.md) — operator-facing configuration for the whole application
- [`docs/adr-plain-jdbc-ddl.md`](docs/adr-plain-jdbc-ddl.md) — ADR on plain JDBC for DDL
