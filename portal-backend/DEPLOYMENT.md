# Portal Backend – Deployment Configuration

---

## Table of Contents

1. [Required for Production](#1-required-for-production)
   - [Database (PostgreSQL)](#11-database-postgresql)
   - [Keycloak / Security](#12-keycloak--security)
   - [Kafka](#13-kafka)
2. [Optional / Tuning](#2-optional--tuning)
   - [Server](#21-server)
   - [Event Publishing & Config-Adapter](#22-event-publishing--config-adapter)
   - [Kafka Producer Tuning](#23-kafka-producer-tuning)
   - [Kafka Consumer](#24-kafka-consumer)
   - [Model Atlas](#25-model-atlas)
   - [Logging](#26-logging)
   - [OpenAPI / Swagger UI](#27-openapi--swagger-ui)
3. [Local Development Only](#3-local-development-only)
   - [Local Defaults](#31-local-defaults)
   - [Seed Data Profile (local-init)](#32-seed-data-profile-local-init)
4. [Spring Profiles](#4-spring-profiles)
5. [docker-compose Example](#5-docker-compose-example)

---

## 1. Required for Production

### 1.1 Database (PostgreSQL)

| Property / Env Var | Example Value | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/portal_backend` | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `admin` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `secret` | Database password |
| `DATASOURCE_ENCRYPTION_KEY` | *(required)* | Key used to encrypt sensitive datasource fields. No default in production config. |
| `DATASOURCE_ENCRYPTION_SALT` | *(required)* | Salt for datasource field encryption. No default in production config. |

> `application-postgres.yaml` defaults to `localhost:5432 / admin / admin` — always override in production.
> `application-local.yaml` sets fallback values for `DATASOURCE_ENCRYPTION_KEY` and `DATASOURCE_ENCRYPTION_SALT` — never use these in production.

---

### 1.2 Keycloak / Security

| Property / Env Var | Example Value | Description |
|---|---|---|
| `KEYCLOAK_AUTH_SERVER_URL` | `https://keycloak.example.com` | Keycloak base URL — used to build the JWK-set URI |
| `KEYCLOAK_ISSUER_URI` | `https://keycloak.example.com` | Used for JWT `iss` claim validation. **Can differ from `AUTH_SERVER_URL`** when the container accesses Keycloak on an internal hostname but tokens are issued to an external one. |
| `KEYCLOAK_REALM` | `civitas-core` | Realm name |
| `KEYCLOAK_TARGET_REALM` | `civitas-core` | Realm used when provisioning users/groups/roles via config-adapter |

---

### 1.3 Kafka

| Property / Env Var | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | *(required)* | Covers both `spring.kafka.bootstrap-servers` (via `${KAFKA_BOOTSTRAP_SERVERS}` placeholder) and `kafka.bootstrap-servers` (via Spring relaxed binding) — only this one env var needed |
| `kafka.enabled` | `false` | **Must be `true`.** Enables the Kafka CloudEvent publisher. Without it, events are only logged, never sent. |
| `kafka.result-topic` | `core.civitas.config.results` | Topic consumed for config-adapter processing results |
| `KAFKA_TOPIC_PREFIX` | `""` | Optional prefix for all Kafka topic names |

---

## 2. Optional / Tuning

### 2.1 Server

| Property / Env Var | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8089` | HTTP port |
| `server.servlet.context-path` | `/v2` | API path prefix |

---

### 2.2 Event Publishing & Config-Adapter

| Property | Default | Description |
|---|---|---|
| `event.config-adapter-timeout-seconds` | `10` | Seconds to wait for config-adapter to reply before throwing `ExternalSystemTimeoutException` |
| `event.publish-timeout-seconds` | `5` | Seconds to wait for broker acknowledgement |
| `event.target-component` | `keycloak` | Target component sent in config events (e.g. `keycloak`, `ldap`) |
| `kafka.result-timeout-ms` | `30000` | Ms the CloudEvent publisher waits for a result |
| `kafka.acks` | `all` | Producer ack mode for the CloudEvent publisher |
| `kafka.retries` | `3` | Retry count for the CloudEvent publisher |

---

### 2.3 Kafka Producer Tuning

Defaults are production-grade. Only change for specific throughput/latency requirements.

| Property | Default | Description |
|---|---|---|
| `spring.kafka.producer.acks` | `all` | Wait for all in-sync replicas |
| `spring.kafka.producer.retries` | `2147483647` | Unlimited retries |
| `spring.kafka.producer.batch-size` | `16384` | Batch size in bytes |
| `spring.kafka.producer.buffer-memory` | `33554432` | Producer buffer in bytes |
| `spring.kafka.producer.properties.enable.idempotence` | `true` | Exactly-once delivery per partition |
| `spring.kafka.producer.properties.max.in.flight.requests.per.connection` | `5` | Max unacknowledged in-flight requests |
| `spring.kafka.producer.properties.compression.type` | `snappy` | Compression codec |
| `spring.kafka.producer.properties.linger.ms` | `10` | Max ms to wait before sending a batch |

---

### 2.4 Kafka Consumer

| Property | Default | Description |
|---|---|---|
| `spring.kafka.consumer.group-id` | `civitas-portal-backend` | Consumer group ID |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | Offset reset when no committed offset exists |
| `spring.kafka.topics.config-adapter-result` | `civitas.config.result` | Result topic consumed by `KafkaConfigResultListener` |

---

### 2.5 Model Atlas

| Property | Default | Description |
|---|---|---|
| `model-atlas.baseUrl` | `http://model-atlas:8080` | Model Atlas base URL |
| `model-atlas.scope` | `default` | Scope for requests |
| `model-atlas.stage` | `draft` | Stage for requests |

---

### 2.6 Logging

| Property | Default | Description |
|---|---|---|
| `logging.level.root` | `INFO` | Root log level |
| `logging.level.org.hibernate` | `INFO` | Hibernate log level |
| `logging.level.org.flywaydb` | `INFO` | Flyway log level |

> Activate the `debug` profile for verbose SQL and Flyway output.

---

### 2.7 OpenAPI / Swagger UI

| Property | Default | Description |
|---|---|---|
| `app.url` | `http://localhost:8089` | Server URL shown in Swagger UI — set to the external-facing URL |
| `springdoc.swagger-ui.enabled` | `true` | Disable in production if desired |
| `springdoc.api-docs.path` | `/v2/api-docs` | OpenAPI JSON path |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI path |

---

## 3. Local Development Only

### 3.1 Local Defaults

Set by `application-local.yaml` and `application-postgres.yaml`. Override via environment variables in all deployed environments.

| Property | Local Default | Override |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/portal_backend` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | `admin` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | `admin` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `keycloak.auth-server-url` | `http://localhost:8080` | `KEYCLOAK_AUTH_SERVER_URL` |
| `keycloak.issuer-uri` | `http://localhost:8080` | `KEYCLOAK_ISSUER_URI` |
| `keycloak.realm` | `civitas-core` | `KEYCLOAK_REALM` |
| `keycloak.target-realm` | `civitas-core` | `KEYCLOAK_TARGET_REALM` |
| `kafka.async-publish` | `true` | Not wired in Java — has no effect |
| `spring.jpa.hibernate.ddl-auto` | `validate` | — |
| `spring.jpa.properties.hibernate.format_sql` | `true` | — |

---

### 3.2 Seed Data Profile (local-init)

Activate with `SPRING_PROFILES_ACTIVE=local,postgres,local-init`. Seeds groups and users on startup. **Never use in production.**

Configured in `application-local-init.yaml` (`@ConfigurationProperties(prefix = "local.init")`).

**Groups** (`local.init.groups[]`):

| Field | Example | Description |
|---|---|---|
| `name` | `Local Admins` | Group name |
| `roleName` | `Tenant Admin` | Role assigned to the group |
| `description` | `Local development admin group` | Optional |

**Users** (`local.init.users[]`):

| Field | Example | Description |
|---|---|---|
| `firstName` | `Developer` | — |
| `lastName` | `User` | — |
| `email` | `dev@civitas.local` | Also used as Keycloak username |
| `externalId` | `00000000-0000-0000-0000-000000000001` | Must match the Keycloak user ID in `dev-environment/keycloak/realm-export.json` |
| `title` | `OTHER` | — |
| `groups` | `["Local Admins"]` | — |

---

## 4. Spring Profiles

| Profile | Purpose |
|---|---|
| `local` | Local development defaults |
| `postgres` | PostgreSQL datasource — required for all environments |
| `local-init` | Seed data on startup — local only |
| `debug` | Verbose SQL/Flyway logging |

> Production: `SPRING_PROFILES_ACTIVE=postgres`

---

## 5. docker-compose Example

```yaml
environment:
  SPRING_PROFILES_ACTIVE: postgres

  # Database
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/portal_backend
  SPRING_DATASOURCE_USERNAME: portal_user
  SPRING_DATASOURCE_PASSWORD: <secret>
  DATASOURCE_ENCRYPTION_KEY: <secret>
  DATASOURCE_ENCRYPTION_SALT: <secret>

  # Keycloak
  KEYCLOAK_AUTH_SERVER_URL: https://keycloak.example.com
  KEYCLOAK_ISSUER_URI: https://keycloak.example.com
  KEYCLOAK_REALM: civitas-core
  KEYCLOAK_TARGET_REALM: civitas-core

  # Kafka
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  kafka.enabled: "true"

  # Optional
  APP_URL: https://api.example.com
  SERVER_PORT: "8089"
```
