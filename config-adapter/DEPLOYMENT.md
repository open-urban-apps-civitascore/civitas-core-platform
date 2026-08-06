# Config Adapter – Deployment Configuration

The config adapter consumes CloudEvents from Kafka, applies configuration to backend services (Keycloak, APISIX, FROST, GeoServer, PostGIS, Apache NiFi), and orchestrates the dataset sagas. It is a plain Java application — not Spring Boot — so there are no Spring profiles and no `SPRING_*` variables.

## How configuration is resolved

Two layers only: **environment variables**, then **`application.properties`** on the classpath, baked into the JAR. **JVM system properties are not consulted** — `-Dkafka.bootstrap.servers=…` has no effect on any property in this document, and the only `-D` flags that work are those a library reads itself (see [Logging](#23-logging)).

Env-var names derive from the property key: **uppercase, then `.` → `_`, then `-` → `_`**. No other transformation applies, so camelCase collapses into a single run — `kafka.bootstrap.servers` → `KAFKA_BOOTSTRAP_SERVERS`, `pipeline.status-topic` → `PIPELINE_STATUS_TOPIC`, `postgis.jdbc.maxPoolSize` → `POSTGIS_JDBC_MAXPOOLSIZE`.

> **An empty env var resolves to the default, not to empty.** `FOO=` is indistinguishable from unset, so a committed value cannot be blanked out by setting its variable to the empty string. Never treat "leave it empty to disable" as an option — use an explicit sentinel value instead.

List-valued properties are comma-separated strings. `${...}` interpolation is active on values read from the properties file.

## 1. Required for Production

### 1.1 Build and Packaging

| Requirement | Value |
|---|---|
| Build command | `mvn package -Pdist -pl config-adapter-application` |
| Artifact | `config-adapter-application/target/config-adapter-application-<version>.jar` |
| Run command | `java $JAVA_OPTS -jar config-adapter.jar` |
| Java | 25 |

> **`-Pdist` is mandatory.** The APISIX, FROST, NiFi, GeoServer, PostGIS and examples modules are added as runtime dependencies only by that profile; without it the shaded JAR contains none of them and startup fails — first with `Adapter '<name>' not found via ServiceLoader`, or, if `adapters` avoids them, with `Required SagaCommandHandlers not registered: [frost, apisix]`. The shade plugin merges every module's `META-INF/services` registrations, so a packaging change that drops that merge breaks all `ServiceLoader` discovery at once.

### 1.2 Kafka

| Property | Env var | Default | Description |
|---|---|---|---|
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker list. Used by both the adapter consumers and the saga orchestrator. |
| `kafka.group.id` | `KAFKA_GROUP_ID` | `config-adapter-group` | Consumer group for the adapter consumers. |
| `flowable.kafka.group.id` | `FLOWABLE_KAFKA_GROUP_ID` | `config-adapter-flowable-group` | Consumer group for the saga trigger consumer. |
| `<adapter>.topics` | `<ADAPTER>_TOPICS` | *(none)* | Comma-separated topics for each enabled adapter. Validated against the topic registry — an unknown value fails startup. An adapter with no topics is **skipped with a warning**. |

Fixed, non-configurable settings: `auto.offset.reset=earliest`, `enable.auto.commit=false`, `acks=all`, `retries=MAX_VALUE`, `enable.idempotence=true`. Sends are synchronous. Hard-coded topics: the saga trigger topic `de.civitascore.dataset.saga.trigger` and the saga result topic `de.civitascore.saga.result`; result events for the event-driven adapters go to the `resultTopic` carried in each incoming event's metadata.

> **No transport security is implemented.** No `security.protocol`, `sasl.*` or `ssl.*` setting is read anywhere and there is no passthrough for arbitrary Kafka client properties, so the adapter can only reach a **PLAINTEXT, unauthenticated** broker; connecting it to a secured cluster is a code change, not a configuration change.

### 1.3 Flowable State Database

The saga orchestrator keeps its state in a dedicated PostgreSQL database. All three values are required — a missing one throws at startup.

| Property | Env var | Default | Description |
|---|---|---|---|
| `flowable.jdbc.url` | `FLOWABLE_JDBC_URL` | *(required)* | JDBC URL of the Flowable database. |
| `flowable.jdbc.username` | `FLOWABLE_JDBC_USERNAME` | *(required)* | Database user. |
| `flowable.jdbc.password` | `FLOWABLE_JDBC_PASSWORD` | *(required)* | Database password. An empty string is accepted; null is not. |

> **The database and its user must already exist.** Flowable creates its own tables on first start but not the database, and pointing the adapter at an absent database produces a crash loop.

The connection pool is fixed at 5 connections and exposes no tuning knobs. Credentials here are passed through verbatim — `ENC(...)` is **not** supported.

### 1.4 Adapter Selection

| Property | Env var | Default | Description |
|---|---|---|---|
| `adapters` | `ADAPTERS` | *(required)* | Comma-separated adapter names to enable. Empty fails startup. |
| `eventhandler.name` | `EVENTHANDLER_NAME` | *(required)* | Event transport. `kafka` is the only implementation. |
| `eventconsumer.name` | `EVENTCONSUMER_NAME` | *(none)* | Alternative to `eventhandler.name`; **mutually exclusive** with it. |
| `eventpublisher.name` | `EVENTPUBLISHER_NAME` | *(none)* | Optional. A missing publisher only logs a warning. |

Selectable adapter names: `keycloak`, `apisix`, `frost`, `geoserver`, `postgis`, `dummylog`.

> **`nifi` is not a valid entry in `adapters`.** The NiFi module registers a saga handler and no event adapter; listing it fails startup with `Adapter 'nifi' not found via ServiceLoader`.
>
> **Saga handlers ignore `adapters` entirely.** Every saga handler on the classpath is loaded and initialised regardless of this list, and one that fails to initialise is dropped with a warning — except `frost` and `apisix`, which are required and abort startup when absent, so a misconfigured `apisix.*` or `frost.*` value kills startup even though the immediate log line reads as a mere dropped handler.

### 1.5 APISIX Gateway Provisioning

Every value here is required and fails fast at startup.

| Property | Env var | Description |
|---|---|---|
| `apisix.admin.url` | `APISIX_ADMIN_URL` | Admin API base URL. Defaults to `http://localhost:9180`. |
| `apisix.admin.key` | `APISIX_ADMIN_KEY` | Admin API key, sent as `X-API-KEY`. No default. |
| `apisix.api.host` | `APISIX_API_HOST` | Public data-plane hostname; becomes each dataset route's `hosts` filter. |
| `apisix.api.public.url` | `APISIX_API_PUBLIC_URL` | Public base URL reported back to portal-backend. Trailing slashes are stripped. |
| `apisix.plugin.config.id` | `APISIX_PLUGIN_CONFIG_ID` | Plugin config attached to **every** dataset route. |
| `apisix.service.id` | `APISIX_SERVICE_ID` | Stamped as `service_id` on every dataset route. |

> **Two of these are security-critical.** `apisix.plugin.config.id` is what attaches OIDC and OPA to a dataset route — a route provisioned without it is reachable without authorization — and `apisix.service.id` is how OPA resolves which backend a request targets, without which OPA rejects every dataset route as an unknown backend. Neither has a default, precisely so that a deployment cannot come up half-secured.

| Property | Env var | Default | Description |
|---|---|---|---|
| `apisix.geoserver.url` | `APISIX_GEOSERVER_URL` | `http://localhost:8080/geoserver` | Upstream for OWS named APIs. Validated only when an OWS route is provisioned. Must be reachable **from the gateway**. |
| `apisix.proxy.rewrite.headers.remove` | `APISIX_PROXY_REWRITE_HEADERS_REMOVE` | *(none)* | Additional request headers to strip on dataset routes. |

> `X-Allowed-Scope-Ids` and `X-Allowed-Pool-Ids` are **always** stripped and cannot be re-enabled; this property only adds further headers to that baseline. Those two headers carry OPA's authorization decision, so a client-supplied copy reaching the backend would be an authorization bypass — see `portal-backend/DEPLOYMENT.md` for the gateway side of the same contract.

The adapter must also be able to authenticate to FROST as an upstream credential provider: set either `apisix.frost.basic.auth.username` + `apisix.frost.basic.auth.password`, or `apisix.frost.api.key` (with optional `apisix.frost.api.key.header`, default `X-API-Key`). Configuring neither fails startup; configuring both makes basic auth win and logs a warning.

### 1.6 FROST

| Property | Env var | Default | Description |
|---|---|---|---|
| `frost.url` | `FROST_URL` | `http://localhost:8080/v1.1` | FROST base URL as the adapter reaches it. Trailing `/` stripped. |
| `frost.public.url` | `FROST_PUBLIC_URL` | falls back to `frost.url` | Externally visible base URL reported in saga results. Set it whenever the internal and external addresses differ. |
| `frost.api.key` | `FROST_API_KEY` | *(none)* | API key. |
| `frost.api.key.header` | `FROST_API_KEY_HEADER` | `X-API-Key` | Header carrying the API key. |
| `frost.basic.auth.username` | `FROST_BASIC_AUTH_USERNAME` | *(none)* | Basic auth user. |
| `frost.basic.auth.password` | `FROST_BASIC_AUTH_PASSWORD` | *(none)* | Basic auth password. |

At least one of basic auth or API key must be set, or startup fails. If both are set, basic auth wins and a warning is logged.

### 1.7 Credential Encryption

| Variable | Format | Description |
|---|---|---|
| `CIVITAS_MASTER_KEY` | 64 hex characters (256 bits) | Master key for decrypting `ENC(...)` values. Must match the key portal-backend encrypted with. |

This is read **directly from the environment** — it is not a configuration property and cannot be placed in `application.properties`. A value that does not decode to exactly 32 bytes fails with an explicit error.

Properties that accept an `ENC(...)` value: `geoserver.admin.password`, `geoserver.postgis.password`, PostGIS **role** passwords carried in event payloads, and NiFi datasource credentials carried in saga payloads.

Everything else takes its value verbatim — notably `postgis.jdbc.password`, `flowable.jdbc.password`, `keycloak.password`, `apisix.admin.key`, `frost.api.key`, `nifi.oidc.client-secret` and `nifi.postgis.password`. Note the asymmetry on the last one: a datasource credential arriving in a saga payload is decrypted, but the `nifi.postgis.password` **property** is forwarded to NiFi as-is.

| Situation | Outcome |
|---|---|
| No key, no `ENC(...)` values anywhere | Starts normally; logs a warning per component. |
| No key, an `ENC(...)` GeoServer password | Fails at **startup**; the GeoServer saga handler is dropped. |
| No key, an `ENC(...)` value arriving in an event or saga payload | Fails **that event or step**, not startup. |
| Wrong key | Decryption fails at use time with an authentication-tag error. Never silent. |

> **Key stretching costs real startup time.** The key is stretched with PBKDF2 at 600 000 iterations, independently in each component that needs it — budget the readiness probe's initial delay accordingly.

## 2. Optional / Tuning

### 2.1 Health Check and Probes

| Property | Env var | Default | Description |
|---|---|---|---|
| `healthcheck.port` | `HEALTHCHECK_PORT` | `8080` in code, `8280` in the packaged `application.properties` | Port for the health endpoints. Binds `0.0.0.0`. A non-integer value fails startup. |

| Path | Meaning | 200 when |
|---|---|---|
| `/health` | Detailed status, including the consumer count | Ready and at least one consumer is running |
| `/health/ready` | Readiness | Startup completed |
| `/health/live` | Liveness | **Always** |

> **Three different ports are in play and they do not agree**: the code default is `8080`, the packaged properties file sets `8280`, and the image's own `HEALTHCHECK` polls `8089`. Set `HEALTHCHECK_PORT` explicitly and make the container healthcheck, the Kubernetes probes and this value agree — otherwise the container reports unhealthy while the application is fine.
>
> **`/health/live` is a constant.** It never fails, so a wedged consumer loop will not be restarted by a liveness probe; use `/health/ready` for readiness and treat liveness as a process-alive check only. Readiness is latched once at startup and is never withdrawn while running; it does not track Kafka partition assignment.

### 2.2 Retry and DLQ

| Property | Env var | Default | Description |
|---|---|---|---|
| `kafka.retry.max.attempts` | `KAFKA_RETRY_MAX_ATTEMPTS` | `3` | Retries for a retryable failure. |
| `kafka.retry.initial.backoff.ms` | `KAFKA_RETRY_INITIAL_BACKOFF_MS` | `1000` | First backoff. Doubles per attempt, capped at 30 s. |
| `kafka.dlq.topic` | `KAFKA_DLQ_TOPIC` | `de.civitascore.configadapter.dlq` | Dead-letter topic. |
| `kafka.publish.timeout.ms` | `KAFKA_PUBLISH_TIMEOUT_MS` | `5000` | Synchronous send timeout. |
| `kafka.max.poll.interval.ms` | `KAFKA_MAX_POLL_INTERVAL_MS` | `300000` | **Validation input only.** |

Classification: a retryable failure is retried with exponential backoff and then dead-lettered; a fatal failure is dead-lettered immediately; any other exception is treated as fatal. DLQ sends are synchronous, and a failed DLQ send deliberately prevents the offset commit so the record is reprocessed. A startup gate rejects a retry configuration whose total backoff exceeds 80 % of `kafka.max.poll.interval.ms`, to stop a long backoff from triggering a consumer-group rebalance.

> **`kafka.max.poll.interval.ms` is never applied to the consumer.** It feeds that validation gate only, so raising it to permit a longer backoff passes the check while leaving the broker's actual limit at the Kafka default and a rebalance can still occur. Change the backoff, not this value.

### 2.3 Logging

Logging is SLF4J-simple, configured by `simplelogger.properties` in the JAR. There is no Logback or Log4j, no `logging.level.*` property, and no runtime log-level endpoint. Defaults: root `info`, `de.civitascore.configadapter` `debug`, `org.apache.kafka` `warn`.

Levels are overridden with JVM system properties, the one place `-D` flags do work — `JAVA_OPTS="$JAVA_OPTS -Dorg.slf4j.simpleLogger.log.de.civitascore.configadapter=debug"`.

### 2.4 Per-Adapter Connection Settings

Consult each module's README for the full property contract and behaviour. Values below are the deployment-relevant defaults.

**Keycloak**

| Property | Env var | Default |
|---|---|---|
| `keycloak.url` | `KEYCLOAK_URL` | `http://localhost:8080` |
| `keycloak.realm` | `KEYCLOAK_REALM` | `master` |
| `keycloak.username` | `KEYCLOAK_USERNAME` | `admin` |
| `keycloak.password` | `KEYCLOAK_PASSWORD` | `admin` |
| `keycloak.client.id` | `KEYCLOAK_CLIENT_ID` | `admin-cli` |
| `keycloak.invitation.client.id` | `KEYCLOAK_INVITATION_CLIENT_ID` | *(none)* |
| `keycloak.invitation.redirect.uri` | `KEYCLOAK_INVITATION_REDIRECT_URI` | *(none)* |

> `keycloak.password` **has a hard-coded fallback of `admin`.** Nothing fails if it is unset — the adapter silently tries `admin` and the connection check then fails at startup with an authentication error rather than a missing-configuration one. Always set it explicitly.

The two `invitation` properties are **both-or-neither**: setting exactly one fails startup. The adapter also verifies the Keycloak connection during startup and aborts if it cannot reach the server.

**GeoServer**

| Property | Env var | Default |
|---|---|---|
| `geoserver.url` | `GEOSERVER_URL` | `http://localhost:8080/geoserver` |
| `geoserver.public.url` | `GEOSERVER_PUBLIC_URL` | falls back to `geoserver.url` |
| `geoserver.admin.user` | `GEOSERVER_ADMIN_USER` | *(required)* |
| `geoserver.admin.password` | `GEOSERVER_ADMIN_PASSWORD` | *(required, accepts `ENC(...)`)* |
| `geoserver.postgis.host` | `GEOSERVER_POSTGIS_HOST` | `localhost` |
| `geoserver.postgis.port` | `GEOSERVER_POSTGIS_PORT` | `5432` |
| `geoserver.postgis.database` | `GEOSERVER_POSTGIS_DATABASE` | `civitas_geo` |
| `geoserver.postgis.user` | `GEOSERVER_POSTGIS_USER` | *(none)* |
| `geoserver.postgis.password` | `GEOSERVER_POSTGIS_PASSWORD` | *(none, accepts `ENC(...)`)* |

The datastore schema is derived per dataset and is not configurable. Enabling the event-driven GeoServer adapter additionally requires `GEOSERVER_TOPICS`, which the packaged properties file does not set.

**PostGIS**

| Property | Env var | Default |
|---|---|---|
| `postgis.jdbc.url` | `POSTGIS_JDBC_URL` | *(required)* |
| `postgis.jdbc.user` | `POSTGIS_JDBC_USER` | *(required)* |
| `postgis.jdbc.password` | `POSTGIS_JDBC_PASSWORD` | `""` |
| `postgis.jdbc.maxPoolSize` | `POSTGIS_JDBC_MAXPOOLSIZE` | `5` |
| `postgis.jdbc.connectionTimeoutMs` | `POSTGIS_JDBC_CONNECTIONTIMEOUTMS` | `5000` |

The PostGIS extension must already be installed in the target database; the adapter does not create it. The pool does not fail startup when the database is unreachable — failures surface per event as retryable.

> **Three database identities must resolve to the same owning role.** No per-schema grants are emitted, so `postgis.jdbc.user`, `geoserver.postgis.user` and `nifi.postgis.user` can only read and write the per-dataset schemas if they share the owning role. Splitting them silently breaks reads and writes rather than failing loudly.

**NiFi**

| Property | Env var | Default |
|---|---|---|
| `nifi.url` | `NIFI_URL` | `https://localhost:8443` |
| `nifi.tls.insecure` | `NIFI_TLS_INSECURE` | `true` |
| `nifi.oidc.token-uri` | `NIFI_OIDC_TOKEN_URI` | a `localhost` realm URL |
| `nifi.oidc.client-id` | `NIFI_OIDC_CLIENT_ID` | `nifi` |
| `nifi.oidc.client-secret` | `NIFI_OIDC_CLIENT_SECRET` | `""` |
| `nifi.oidc.scope` | `NIFI_OIDC_SCOPE` | *(none)* |
| `nifi.runtime-monitor.interval-ms` | `NIFI_RUNTIME_MONITOR_INTERVAL_MS` | `5000` |
| `nifi.frost.url` | `NIFI_FROST_URL` | *(none)* |
| `nifi.postgis.url` / `.user` / `.password` | `NIFI_POSTGIS_URL` / `_USER` / `_PASSWORD` | *(none)* |
| `nifi.master-key` | `NIFI_MASTER_KEY` | falls back to `CIVITAS_MASTER_KEY` |

`nifi.frost.basic.auth.username` / `.password` fall back to the unprefixed `frost.basic.auth.*`. Most NiFi misconfiguration fails the first affected pipeline deployment rather than startup.

> **A TLS MQTT source needs a deployment-provisioned Parameter Context.** When a datasource sets `tls.enabled`, the generated flow adds an SSL context service that validates the broker against the NiFi node truststore and references a Parameter Context named `NiFi Node Truststore` holding a sensitive parameter `TRUSTSTORE_PASSWORD`; the flow declares that context but carries no value for it. Provision the context on the NiFi node with the truststore password, resolve the truststore file from `TRUSTSTORE_PATH` in the node's environment, and ensure that truststore contains the broker's CA. Otherwise the pipeline deploys clean and then fails at runtime when the processor connects.

**Flowable** — see [§1.3](#13-flowable-state-database).

## 3. Security Hardening

Four settings ship with development-friendly defaults that are wrong for production.

| # | Setting | Ships as | Set to | Why |
|---|---|---|---|---|
| 1 | `NIFI_TLS_INSECURE` | `true` | **`false`** | `true` disables certificate validation **and** hostname verification for every NiFi call. Only the NiFi client is affected — the OIDC token endpoint always validates, so the client secret is never posted over an unverified connection. |
| 2 | `KEYCLOAK_PASSWORD` | falls back to `admin` | an injected secret | See [§2.4](#24-per-adapter-connection-settings). |
| 3 | Kafka transport | PLAINTEXT only | *(not configurable)* | No TLS or SASL support exists. Isolate the broker at the network level. |
| 4 | Every `localhost` default | `localhost` | real hostnames | `kafka.bootstrap.servers`, `keycloak.url`, `apisix.admin.url`, `frost.url`, `geoserver.url`, `nifi.url`, `nifi.oidc.token-uri`, `postgis.jdbc.url` and `flowable.jdbc.url` all default to a local address. |

Custom certificate authorities: only NiFi has a verification flag. The APISIX, FROST, GeoServer and Keycloak clients use the JVM default trust store, so a private CA needs either `-Djavax.net.ssl.trustStore…` in `JAVA_OPTS` or a trust store baked into the image. MQTT-over-TLS trust belongs to NiFi rather than the adapter: the generated flow validates the broker against the NiFi node truststore, which must therefore carry the broker's CA and be unlocked by the `NiFi Node Truststore` Parameter Context — see [§2.4](#24-per-adapter-connection-settings).

Secrets to inject rather than bake in: `CIVITAS_MASTER_KEY`, `APISIX_ADMIN_KEY`, `KEYCLOAK_PASSWORD`, `FLOWABLE_JDBC_PASSWORD`, `POSTGIS_JDBC_PASSWORD`, `GEOSERVER_ADMIN_PASSWORD`, `GEOSERVER_POSTGIS_PASSWORD`, `NIFI_OIDC_CLIENT_SECRET`, and the FROST credentials.

## 4. Container Image

| Property | Value |
|---|---|
| Base image | `eclipse-temurin:25-jre-alpine` |
| User | non-root, uid/gid `1001` |
| Timezone | `Europe/Berlin` |
| Exposed port | `8089` |
| Container healthcheck | `curl -f http://localhost:8089/health/ready`, every 30 s, 10 s timeout, 60 s start period, 3 retries |
| `JAVA_OPTS` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom` |

- **`gcompat` is a functional dependency, not convenience.** Snappy's native library is glibc-linked and inbound Kafka messages may be snappy-compressed, so removing the shim from an Alpine base breaks message consumption at runtime.
- **The `PORT` variable is read by nothing.** The listening port comes from `HEALTHCHECK_PORT`; `PORT` only documents the exposed port.

## 5. Operational Behaviour

- **Startup order.** Configuration is loaded, saga handlers are discovered and initialised, one Kafka consumer is created per enabled adapter, the health server starts, and readiness is latched once everything is running.
- **Shutdown.** `SIGTERM` only writes a log line — it does not initiate cleanup. Shutdown completes when the JVM exits, and consumer stop waits are capped at 5 seconds. In-flight saga steps are abandoned rather than drained, so set a `terminationGracePeriodSeconds` that comfortably exceeds the longest external call; the orchestrator resumes abandoned work from the Flowable database on the next start.
- **Threading.** Virtual threads throughout, with no concurrency knobs. Event processing is strictly **sequential per adapter**: one record at a time, committed individually. Retry backoff blocks that adapter's consumer thread, so a slow external system delays every subsequent event for that adapter — the main reason to keep the backoff configuration modest.
- **Scaling.** Each replica runs its own Flowable async executor against the shared state database and joins the same consumer groups. Kafka partitioning divides the event load, and Flowable's job locking is what prevents two replicas from executing the same saga step; nothing else in this application coordinates replicas. Validate a multi-replica configuration in a staging environment before relying on it.
- **Pipeline monitoring.** When the NiFi handler is active, a background thread polls NiFi and publishes pipeline state **transitions** to the pipeline status topic. Recovery requires three consecutive healthy polls, so a flapping pipeline cannot flood the topic.

## 6. docker-compose Example

```yaml
environment:
  # Adapter selection
  ADAPTERS: keycloak,apisix,frost,geoserver,postgis
  EVENTHANDLER_NAME: kafka
  # Kafka
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  KAFKA_GROUP_ID: config-adapter
  HEALTHCHECK_PORT: "8089"
  # Flowable saga state (database and user must already exist)
  FLOWABLE_JDBC_URL: jdbc:postgresql://postgres:5432/flowable
  FLOWABLE_JDBC_USERNAME: flowable
  FLOWABLE_JDBC_PASSWORD: <secret>
  # Credential encryption (shared with portal-backend)
  CIVITAS_MASTER_KEY: <hex-encoded 256-bit key>
  # Keycloak
  KEYCLOAK_URL: https://keycloak.example.com
  KEYCLOAK_REALM: civitas-core
  KEYCLOAK_PASSWORD: <secret>
  # APISIX
  APISIX_ADMIN_URL: http://apisix:9180
  APISIX_ADMIN_KEY: <secret>
  APISIX_API_HOST: api.example.com
  APISIX_API_PUBLIC_URL: https://api.example.com
  APISIX_PLUGIN_CONFIG_ID: "1"
  APISIX_SERVICE_ID: <service id>
  APISIX_FROST_API_KEY: <secret>
  # FROST
  FROST_URL: http://frost:8080/FROST-Server/v1.1
  FROST_PUBLIC_URL: https://api.example.com/FROST-Server/v1.1
  FROST_API_KEY: <secret>
  # GeoServer
  GEOSERVER_URL: http://geoserver:8080/geoserver
  GEOSERVER_PUBLIC_URL: https://api.example.com/geoserver
  GEOSERVER_ADMIN_USER: admin
  GEOSERVER_ADMIN_PASSWORD: <secret>
  GEOSERVER_POSTGIS_HOST: postgres
  GEOSERVER_POSTGIS_USER: geo
  GEOSERVER_POSTGIS_PASSWORD: <secret>
  # PostGIS
  POSTGIS_JDBC_URL: jdbc:postgresql://postgres:5432/civitas
  POSTGIS_JDBC_USER: civitas
  POSTGIS_JDBC_PASSWORD: <secret>
  # NiFi
  NIFI_URL: https://nifi:8443
  NIFI_TLS_INSECURE: "false"
  NIFI_OIDC_TOKEN_URI: https://keycloak.example.com/realms/civitas-core/protocol/openid-connect/token
  NIFI_OIDC_CLIENT_SECRET: <secret>
  NIFI_FROST_URL: http://frost:8080/FROST-Server/v1.1
  NIFI_POSTGIS_URL: jdbc:postgresql://postgres:5432/civitas
  NIFI_POSTGIS_USER: civitas
  NIFI_POSTGIS_PASSWORD: <secret>
```

Each enabled adapter additionally needs its `<ADAPTER>_TOPICS` value unless the packaged `application.properties` already carries one.
