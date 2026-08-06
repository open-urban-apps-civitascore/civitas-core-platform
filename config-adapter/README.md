# Civitas Config Adapter

Multi-module framework that consumes CloudEvents describing desired configuration and applies that configuration to backend services: identity, gateway, geo, sensor and database services. It is a one-way projection — the framework reads events, writes to backends, and publishes a result event per command. It is not a source of truth: it does not read configuration back out of the backends, does not reconcile drift, and exposes no API of its own.

## Modules

`pom.xml` `<modules>` is the authority on which modules exist. `config-adapter-api` is the root of the dependency graph and depends on no other module in this build; every other module depends on it, and adapters never depend on one another. `config-adapter-application` is the only module that depends on `config-adapter-configuration` at compile scope, and it hands that implementation to adapters as the narrower `AdapterConfig`, so an adapter can read properties but cannot reach application-level configuration.

| Module | Responsibility | Extension points | Details |
|---|---|---|---|
| `config-adapter-api` | Contracts and event models: `ConfigAdapter`, `AbstractConfigAdapter`, `SagaCommandHandler`, `EventConsumer`, `EventPublisher`, `AdapterConfig`, `ApplicationConfig`, `Topics`, `AdapterErrorCode`, credential decryption | — | — |
| `config-adapter-configuration` | Layered configuration over Apache Commons Configuration2; environment variables override properties files | — | — |
| `event-handler-kafka` | Kafka consumer and publisher, CloudEvent binding, retry and dead-letter handling | `kafka` (consumer, publisher) | — |
| `config-adapter-application` | Entry point. Resolves configuration, discovers implementations by name, wires one consumer per adapter, serves health endpoints, orchestrates shutdown | — | — |
| `config-adapter-flowable` | Embedded Flowable BPMN engine, saga trigger consumer, result publisher, handler registry | — | [README](config-adapter-flowable/README.md) |
| `config-adapter-keycloak` | Realms, clients, users, roles and groups via the Keycloak Admin REST API | `keycloak` (adapter) | [README](config-adapter-keycloak/README.md) |
| `config-adapter-apisix` | Gateway upstreams and routes via the APISIX Admin API; dataset route provisioning | `apisix` (adapter, saga) | [README](config-adapter-apisix/README.md) |
| `config-adapter-frost` | SensorThings entities and FROST projects via the FROST-Server API | `frost` (adapter, saga) | [README](config-adapter-frost/README.md) |
| `config-adapter-geoserver` | Workspaces, datastores, feature types, layers and styles via the GeoServer REST API | `geoserver` (adapter, saga) | [README](config-adapter-geoserver/README.md) |
| `config-adapter-nifi` | Translates the engine-neutral pipeline graph into a NiFi flow and deploys it over the NiFi REST API | `nifi` (saga only) | [README](config-adapter-nifi/README.md) |
| `config-adapter-postgis` | Tables, schemas, database roles and schema grants via DDL over JDBC | `postgis` (adapter, saga) | [README](config-adapter-postgis/README.md) |
| `config-adapter-examples` | `dummylog` adapter, which logs the events it receives; adapter skeleton and test fixture | `dummylog` (adapter) | — |

`config-adapter-nifi` registers a `SagaCommandHandler` and no `ConfigAdapter`. It is reachable only through saga dispatch, is unreachable over the CloudEvent path, and **MUST NOT** appear in `adapters` — no adapter of that name exists to discover, so startup fails.

## Adapter contract

Adapters, event consumers and event publishers are discovered through `ServiceLoader` and selected **by short name**, never by class name. The short name is the unit of configuration: `adapters=keycloak,apisix` selects the adapters whose `getName()` returns those values, and `eventhandler.name=kafka` selects the handler. Two extension points exist, registered independently; a module may register either or both.

| Extension point | Registered under `META-INF/services/` | Reached by | Selected via |
|---|---|---|---|
| `ConfigAdapter` | `de.civitascore.configadapter.adapter.ConfigAdapter` | CloudEvents on Kafka topics | the `adapters` property |
| `SagaCommandHandler` | `de.civitascore.configadapter.adapter.SagaCommandHandler` | in-process saga step dispatch | the handler's `adapter()` name |

A `SagaCommandHandler` is not listed in `adapters` and subscribes to no topics. It is held in a registry keyed on its `adapter()` name and invoked in-process by the orchestrator.

## Event flow

1. A consumer polls its subscribed topics and deserializes each record into a `ConfigEvent`.
2. The event is dispatched to the adapter that consumer owns.
3. The adapter applies the change to its backend service.
4. A `ConfigResultEvent` is published to the `resultTopic` named in the event metadata, if one is present.

Each adapter gets its own consumer subscribed only to that adapter's topics, so topic filtering happens at the broker rather than after consumption. Consumers are independent: one adapter failing does not stop the others, and each consumer closes its own adapter on shutdown.

## Saga orchestration

Multi-step dataset provisioning runs as a Flowable BPMN saga rather than as independent CloudEvents, because its steps must compensate as a unit. Three topics carry the whole flow: the trigger `de.civitascore.dataset.saga.trigger`, the result `de.civitascore.saga.result`, and pipeline status on `pipeline.status-topic` (default `de.civitascore.pipeline.status`). There are no per-adapter saga topics — once a saga is running, each step resolves its `SagaCommandHandler` from the registry by adapter name and calls it in-process, and a failure drives the BPMN compensation path.

## Event contract

The `data` section of an incoming CloudEvent deserializes into a `ConfigEvent` of `metadata` and `payload`. `metadata` carries `messageId` (identifier of this command, echoed into the result), `timestamp` (emission time), `source` (emitting system), `correlationId` (correlates command and result across systems), `configVersion` (version of the payload schema) and `resultTopic` (topic to publish the result to; no result is published when absent). `payload` carries `targetComponent` (resource kind the adapter routes on, for example `user`), `targetResource` (resource the operation applies to), `operation` and `config` (desired state: a `path` and a `value`).

`operation` is a closed enum of `CREATE`, `UPDATE` and `DELETE`. An event carrying any other value is rejected as a deserialization failure and never reaches an adapter.

A `ConfigResultEvent` is published to `resultTopic` as a CloudEvent whose `source` is `de.civitascore.config-adapter.<adapterName>`. Its fields appear both as CloudEvent extension attributes (lowercased, for example `correlationid`) and in the JSON body. Always present: `correlationId` copied from the command, `originalMessageId` holding the command's `messageId`, `status` (`SUCCESS` or `FAILURE`), `operation`, `targetResource`, `message` (symbolic outcome on success, safe description on failure), `timestamp` and `source`. On success only: `resourceId`. On failure only: `errorCode`.

### Topics

Topics follow `de.civitascore.<domain>.<resource>.<action>`. `Topics` is the authority on the registry; matching trims whitespace and ignores case. The families are identity (`de.civitascore.idm.*` — users including password changes and lock state, realms, clients, groups, roles), gateway (`de.civitascore.api.*` — backends and routes), data (`de.civitascore.data.*` — SensorThings entities, FROST projects, pipelines, and SQL tables, schemas and roles) and geo (`de.civitascore.geo.*` — workspaces, datastores, feature types, layers and styles).

An adapter reads its topics from `<adapterName>.topics`. Every entry is validated against `Topics` during initialization, so a typo fails startup rather than silently subscribing to nothing.

### Error codes

Codes are banded: `1xxx` validation (fatal), `2xxx` connectivity (retryable), `3xxx` adapter-specific with one band per adapter — `30xx` Keycloak, `31xx` APISIX, `32xx` FROST, `34xx` GeoServer, `35xx` PostGIS, `36xx` NiFi — and `9xxx` unexpected. Retryable codes drive the backoff loop; the rest go straight to the dead-letter queue. Each code also carries an internal log template, which stays in the logs and out of the result event.

Each adapter's README lists the codes that adapter raises. The codes below are framework-wide or have no module README of their own:

| Code | Name | Retryable | External message |
|---|---|---|---|
| 1003 | `MISSING_CONFIG` | No | Configuration error |
| 2001 | `CONNECTION_TIMEOUT` | Yes | Service temporarily unavailable |
| 2004 | `RATE_LIMITED` | Yes | Service temporarily unavailable |
| 2005 | `PUBLISH_ERROR` | Yes | Message delivery failed |
| 2006 | `PUBLISH_TIMEOUT` | Yes | Message delivery timeout |
| 9001 | `UNKNOWN_ERROR` | No | Internal error |
| 9002 | `SERIALIZATION_ERROR` | No | Data processing error |
| 9003 | `DESERIALIZATION_ERROR` | No | Data processing error |

## Guarantees

- **Configuration errors fail startup.** An unknown topic in `<adapterName>.topics`, a missing required property, or `eventhandler.name` set alongside `eventconsumer.name` or `eventpublisher.name` aborts startup rather than degrading at runtime. Either `eventhandler.name` or `eventconsumer.name` **MUST** be set.
- **Operations are idempotent.** Re-delivering a command converges on the same state; adapters treat "already exists" and "already absent" as success. Redelivery after a crash is therefore safe.
- **Retryable and fatal are distinct.** A retryable failure is retried with backoff and keeps its partition position; a fatal failure is dead-lettered immediately. Nothing is dropped silently.
- **One poison record cannot stall a partition.** Offsets are committed per record, and a record that exhausts its retries is diverted and committed.
- **Adapters see property access only.** They receive `AdapterConfig`, so no adapter can read or alter application-level configuration.
- **Result and dead-letter messages are safe to forward.** External messages carry no PII and no stack traces; detail stays in the logs.
- **Secrets are never baked into the image.** Credentials arrive as environment variables at runtime, and `ENC(...)` values are decrypted in-process with the master key.

## Error handling

A retryable failure is retried in place with exponential backoff of `initialBackoffMs × 2^(attempt-1)`; once the attempt budget is spent the record is published to the dead-letter topic and its offset committed. A fatal failure skips the retry loop. Publishing to the dead-letter topic is synchronous: if it fails, the offset is not committed and the record is redelivered on the next poll rather than lost. The saga trigger consumer applies the same backoff but has no dead-letter topic — permanent failures such as malformed JSON are skipped immediately, transient failures are retried and then skipped, and the saga timeout mechanism drives compensation. Attempt budget, initial backoff and the dead-letter topic are operator settings; see [DEPLOYMENT.md](DEPLOYMENT.md).

Dead-lettered records keep their original body and gain extension attributes describing the diversion: `dlqerrorcode` (numeric error code), `dlqerrormsg` (external error message), `dlqoriginaltopic` (topic the record arrived on), `dlqtimestamp` (diversion time, ISO 8601) and `dlqretrycount` (the configured retry budget; a fatal failure is diverted without consuming it).

## Configuration

Each property resolves from the environment variable first, then `application.properties`, then the default value in code. A property key maps to an environment variable name by uppercasing it and replacing both dots and dashes with underscores: `kafka.bootstrap.servers` becomes `KAFKA_BOOTSTRAP_SERVERS`, and `nifi.runtime-monitor.interval-ms` becomes `NIFI_RUNTIME_MONITOR_INTERVAL_MS`.

An environment variable set to the empty string resolves as unset, so the properties file or the code default applies. A property therefore cannot be switched off by blanking its variable, and no property may be documented as "leave empty to disable" — an optional feature needs an explicit sentinel value instead.

Every adapter reads its topics from `<adapterName>.topics` and its backend settings from keys prefixed with its short name; each adapter's own README documents its properties. Secrets — admin keys, passwords, client secrets, the master key — have no defaults and are supplied per environment. Several are required with no fallback and fail startup when absent, `apisix.admin.key` among them: a default would let a deployment come up reachable but unauthenticated. [DEPLOYMENT.md](DEPLOYMENT.md) is the sole home for all environment variables, ports, health and probe endpoints, secret handling, Kafka tuning, and the Docker Compose and Kubernetes examples.

### Alignment with the deployment repository

Deployed environments are configured from the separate `civitas-core-deployment` repository, whose `config-adapters` component pins the image tag and supplies the environment variables per environment. That repository and this one are versioned independently, so a change to the runtime contract here takes effect only once it is matched there. Three cases break a deployment rather than degrade it:

- **`ADAPTERS` MUST name only adapters the deployed image provides.** A name with no registered `ConfigAdapter` aborts startup, so removing or renaming an adapter here requires the same edit there in the same release.
- **A new property without a default MUST be supplied there first.** Startup fails on the missing value, so the deployment values have to carry it before the image is rolled out.
- **A renamed or removed topic MUST be reflected in the per-adapter `*_TOPICS` values.** An unknown topic name is rejected against the topic registry at startup.

Confirm the deployed image tag and environment values in that repository before releasing a change to any of the three.

## Extending

Depend on `config-adapter-api` and extend `AbstractConfigAdapter`, which owns topic parsing and validation, result publishing and failure wrapping. Subclasses supply the adapter name, the result type and the per-event logic:

```java
public class MyServiceAdapter extends AbstractConfigAdapter {
  @Override
  public String getName() {
    return "myservice";
  }

  @Override
  protected String getResultType() {
    return "de.civitascore.myservice.processing.result";
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Operation operation = event.payload().operation();
    String resourceId = event.payload().targetResource();
    switch (operation) {
      case CREATE, UPDATE -> applyConfig(resourceId, event.payload().config());
      case DELETE -> removeConfig(resourceId);
    }
    publishSuccessResult(event, "MYSERVICE_" + operation + "_SUCCESS", resourceId);
  }
}
```

The backend calls throw `RetryableAdapterException` for transient failures and `FatalAdapterException` for input the backend rejects; a delete treats "already absent" as success. `close()` defaults to a no-op — override it to release backend connections. `getSubscribedTopics()` is inherited and reads `myservice.topics`; there is nothing to override. `processConfigEvent` is final: it wraps `doProcessConfigEvent`, publishes a failure result for fatal errors, and rethrows so the consumer can retry or dead-letter. Throwing the right exception type is the whole error contract.

Register the implementation in `src/main/resources/META-INF/services/de.civitascore.configadapter.adapter.ConfigAdapter` as a line holding its fully qualified class name, then select it and give it topics: `adapters=myservice`, `eventhandler.name=kafka`, `myservice.topics=de.civitascore.idm.user.created,de.civitascore.idm.user.updated`.

To consume from a broker other than Kafka, implement `EventConsumer`: `initialize` receives the application configuration and the single adapter this consumer owns, `start` **MUST** be non-blocking, and `close` releases the transport and closes the adapter. Deserialization does not need reimplementing — construct a `CloudEventProcessor` around the adapter in `initialize` and call `handleEvent(topic, cloudEvent)` per message; it throws on failure, leaving the retry and dead-letter policy to the consumer. Register the class in `META-INF/services/de.civitascore.configadapter.messaging.EventConsumer` and select it with `eventconsumer.name=<name>`.

## Building and testing

All commands run from the `config-adapter` directory.

```bash
mvn clean install                      # build and unit-test every module
mvn test                               # unit tests only, no Docker
mvn verify                             # integration tests plus PMD, CPD and SpotBugs; needs Docker
mvn test -pl config-adapter-frost      # single module
mvn test -Dtest=ApisixAdapterTest      # single test class
mvn test -T 2                          # modules in parallel; Testcontainers binds random ports
mvn spotless:apply                     # Google Java Format and license headers
mvn package -Pdist -pl config-adapter-application   # the runnable fat JAR
```

A test's name decides its runner: `*Test` runs under Surefire in `mvn test`, `*IT` under Failsafe in `mvn verify`. Any container-based test **MUST** be named `*IT`, or `mvn test` starts containers. Testcontainers images are pinned centrally in `TestContainerImages`. Static analysis is bound to `verify`, so a green `mvn test` is not a green build. Adapter plugins are contributed by the `dist` profile: omitting `-Pdist` produces a JAR holding the framework but no adapter plugins, which starts and then fails when it cannot discover the configured adapter, reporting that the adapter was not found via `ServiceLoader`.

## Local development

`docker-compose.yml` in this directory starts Zookeeper, Kafka, Keycloak and Keycloak's PostgreSQL; that file is the authority on ports and image versions. The stack is identity-scoped: adapters targeting other backends need those backends supplied separately, for example from `dev-environment/`. The Flowable engine needs its own database and user in that PostgreSQL instance — an init script creates them, but a Postgres entrypoint init script runs only when the data volume is first initialized, so against an existing volume the database has to be created by hand before the application will start.

```bash
docker compose up -d
java -jar config-adapter-application/target/config-adapter-application-*.jar
```

## License

EUPL-1.2 — see [LICENSE](LICENSE).
