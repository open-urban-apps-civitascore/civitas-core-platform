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

`-Pdist` is required: without it the JAR carries no adapter plugins and the bootstrap fails with `Adapter not found via ServiceLoader`.

PMD, CPD and SpotBugs are bound per-module at `verify` with `failOnViolation`/`failOnError`, so a green `mvn test` is not a green build. Rulesets live in `<module>/pmd-rules.xml` and `<module>/spotbugs-exclude.xml`.

[`README.md`](README.md) owns the build and test documentation; add build detail there rather than here.

## Architecture

[`README.md`](README.md) owns the module map, the two `ServiceLoader` SPIs, the event flow and the error-code bands; `pom.xml` `<modules>` is the authority on which modules exist. Two conventions neither states:

- Adapters extend `AbstractConfigAdapter` and implement `doProcessConfigEvent()`. `processConfigEvent` is `final` and owns error wrapping and failure-result publishing, so throwing the right exception type is the whole error contract.
- Config value types live in `config-adapter-api` under `model/<service>/`. `IdmConfigValue` and `PostgisConfigValue` are `sealed`, so their `permits` clauses are the authority on the variants. `FrostConfigValue` and `GeoServerConfigValue` deviate from that pattern and serialise through `toApiMap()`, the GeoServer one returning `additionalProperties` unchanged.

## Conventions

- **Formatting**: Google Java Format enforced by Spotless; run `mvn spotless:apply` before committing
- **License**: EUPL-1.2 header auto-inserted on all Java files by Spotless
- **Logging**: All user-controlled strings in log statements use `Encode.forJava(...)` (OWASP Encoder)
- **Imports**: Always use Java imports, never fully qualified class names
- **Dependency direction**: adapters take `AdapterConfig`, never `AppConfig`; keep `config-adapter-configuration` a compile dependency of `config-adapter-application` alone

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

## Configuration

Runtime config is `config-adapter-application/src/main/resources/application.properties`, layered under env vars; [`DEPLOYMENT.md`](DEPLOYMENT.md) owns the resolution order. Env-var names uppercase the key and replace dots **and** dashes with underscores, so `nifi.runtime-monitor.interval-ms` → `NIFI_RUNTIME_MONITOR_INTERVAL_MS`. Two consequences when writing code or docs:

- The properties file overrides the code default, so a constant in an adapter is the effective default only for a key that file omits.
- An empty env var is indistinguishable from an unset one. A property MUST NOT be documented as "leave empty to disable" — use an explicit sentinel value.

## Local Development

`docker compose up -d` at the module root starts Zookeeper, Kafka, Keycloak and PostgreSQL; `docker-compose.yml` is the authority on ports and image versions. This stack is independent of `dev-environment/`.

`config-adapter-flowable` needs its own `flowable` database and user, created by `docker/postgres/init-flowable.sql` mounted into `/docker-entrypoint-initdb.d/`. PostgreSQL runs init scripts **only when initializing an empty data volume**, so a volume carrying data from any earlier start has no `flowable` database and the adapter crash-loops on startup. `docker compose down -v && docker compose up -d` recreates the volume and re-runs the script.

## Documentation

Each fact has one home. Read the document that owns an area before changing code in it, and update that document in the same change.

| Document | Owns |
|---|---|
| [`README.md`](README.md) | Framework contract, event flow, module map, error-code bands, build and test commands, alignment with the deployment repository |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | Every environment variable, port, probe, secret and operational behaviour |
| `config-adapter-<name>/README.md` | That adapter's operations, invariants, properties and error codes |
| [`docs/adr-plain-jdbc-ddl.md`](docs/adr-plain-jdbc-ddl.md) | Why DDL is applied over plain JDBC |

What obliges a documentation edit in the same change:

- A new or renamed property, or a changed startup requirement → `DEPLOYMENT.md`
- A new `AdapterErrorCode` → the error-code table of the module that raises it
- A changed saga step or branch condition → `config-adapter-flowable/README.md` and the module owning the step
- A renamed adapter, a new property without a default, or a renamed topic → the alignment rules in `README.md`, since each breaks a deployment rather than degrading it

State the value that ships, not the constant in the code. The adapter READMEs record behaviour that is expensive to re-derive — idempotency handling, HTTP status quirks, rejection rules — so consult them rather than re-reading the adapter.
