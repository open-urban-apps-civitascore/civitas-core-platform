# Portal Backend – Deployment Configuration

---

## Table of Contents

1. [Required for Production](#1-required-for-production)
   - [Database (PostgreSQL)](#11-database-postgresql)
   - [Keycloak / Security](#12-keycloak--security)
   - [Kafka](#13-kafka)
   - [Model Atlas](#14-model-atlas)
2. [Optional / Tuning](#2-optional--tuning)
   - [Server](#21-server)
   - [Event Publishing & Config-Adapter](#22-event-publishing--config-adapter)
   - [Kafka Producer Tuning](#23-kafka-producer-tuning)
   - [Kafka Consumer](#24-kafka-consumer)
   - [Logging](#25-logging)
   - [OpenAPI / Swagger UI](#26-openapi--swagger-ui)
   - [Security Permit Paths](#27-security-permit-paths)
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
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/portal_backend?sslmode=require` | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `admin` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `secret` | Database password |
| `CIVITAS_MASTER_KEY` | `0000...0000` (64 hex chars) | 256-bit master key for credential encryption, hex-encoded. Shared with config-adapter. Stretched via PBKDF2 at startup; per-credential keys derived via HKDF-Expand. |

> `application-postgres.yaml` defaults to `localhost:5432 / admin / admin` with `sslmode=require` — always override `SPRING_DATASOURCE_URL` in production with your actual host and credentials.
> `application-local.yaml` sets a fallback value for `CIVITAS_MASTER_KEY` — never use it in production.

---

### 1.2 Keycloak / Security

| Property / Env Var | Example Value | Description |
|---|---|---|
| `KEYCLOAK_AUTH_SERVER_URL` | `https://keycloak.example.com` | Keycloak base URL — JWK-set URI is derived from this |
| `KEYCLOAK_ISSUER_URI` | `https://keycloak.example.com` | JWT `iss` claim validation. **Can differ from `KEYCLOAK_AUTH_SERVER_URL`** when the container reaches Keycloak on an internal hostname but tokens carry an external issuer. |
| `KEYCLOAK_REALM` | `civitas-core` | Realm name |
| `KEYCLOAK_TARGET_REALM` | `civitas-core` | Realm used when provisioning users/groups/roles via config-adapter |

---

### 1.3 Kafka

| Property / Env Var | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | *(required)* | Sets both `spring.kafka.bootstrap-servers` and `kafka.bootstrap-servers` — only one env var needed |
| `kafka.enabled` | `false` | **Must be `true`.** Enables the Kafka CloudEvent publisher. Without it, events are only logged, never sent. |
| `kafka.result-topic` | `de.civitascore.config.results` | Topic on which config-adapter publishes processing results |

---

### 1.4 Model Atlas

| Property / Env Var | Default | Description |
|---|---|---|
| `model-atlas.baseUrl` | `http://model-atlas:8080` | Model Atlas base URL |
| `model-atlas.scope` | `default` | Scope for requests |
| `model-atlas.stage` | `draft` | Stage for requests |

---

## 2. Optional / Tuning

### 2.1 Server

| Property / Env Var | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8089` | HTTP port |
| `server.servlet.context-path` | `/v1` | API path prefix |
| `server.max-http-request-header-size` | `32KB` | Max HTTP request header size — increase if users belong to many dataspaces (large `X-Allowed-Scope-Ids` header from OPA) |

---

### 2.2 Event Publishing & Config-Adapter

| Property | Default | Description |
|---|---|---|
| `event.config-adapter-timeout-seconds` | `10` | Seconds to wait for config-adapter to reply before throwing `ExternalSystemTimeoutException` |
| `kafka.result-timeout-ms` | `30000` | How long (ms) the CloudEvent publisher waits for a config-adapter result |
| `kafka.acks` | `all` | Producer ack mode for the CloudEvent publisher |
| `kafka.retries` | `3` | Retry count for the CloudEvent publisher |

---

### 2.3 Kafka Producer Tuning

Defaults are tuned for production. Only adjust for specific throughput/latency requirements.

| Property | Default | Description |
|---|---|---|
| `spring.kafka.producer.acks` | `all` | All in-sync replicas must acknowledge |
| `spring.kafka.producer.retries` | `2147483647` | Unlimited retries |
| `spring.kafka.producer.batch-size` | `16384` | Batch size in bytes |
| `spring.kafka.producer.buffer-memory` | `33554432` | Producer buffer in bytes |
| `spring.kafka.producer.properties.enable.idempotence` | `true` | Exactly-once delivery per partition |
| `spring.kafka.producer.properties.max.in.flight.requests.per.connection` | `5` | Max unacknowledged in-flight requests |
| `spring.kafka.producer.properties.compression.type` | `snappy` | Compression codec. **`snappy` requires glibc** — on Alpine/musl images (e.g. CI) use `lz4` instead (`SPRING_KAFKA_PRODUCER_PROPERTIES_COMPRESSION_TYPE=lz4`). |
| `spring.kafka.producer.properties.linger.ms` | `10` | Max ms to wait before sending a batch |

---

### 2.4 Kafka Consumer

| Property | Default | Description |
|---|---|---|
| `spring.kafka.consumer.group-id` | `civitas-portal-backend` | Consumer group ID |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | Offset reset when no committed offset exists |

---

### 2.5 Logging

| Property | Default | Description |
|---|---|---|
| `logging.level.root` | `INFO` | Root log level |
| `logging.level.org.hibernate` | `INFO` | Hibernate log level |
| `logging.level.org.flywaydb` | `INFO` | Flyway log level |

> Activate the `debug` profile for verbose SQL and Flyway output.

---

### 2.6 OpenAPI / Swagger UI

| Property | Default | Description |
|---|---|---|
| `app.url` | `http://localhost:8089` | Server URL shown in Swagger UI — set to the external-facing URL |
| `springdoc.swagger-ui.enabled` | `true` | Set to `false` in production to hide API docs |
| `springdoc.api-docs.path` | `/v1/api-docs` | OpenAPI JSON path |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI path |

---

### 2.7 Security Permit Paths

Paths that are accessible **without authentication**. Defined in `application.yaml` via `security.permit-paths` and bound to `SecurityProperties`.

| Property | Default | Description |
|---|---|---|
| `security.permit-paths` | `/actuator/health/**`, `/actuator/info` | List of URL patterns permitted without JWT authentication |

Production defaults to actuator endpoints only. The `local` profile adds Swagger/API docs paths (`/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/swagger-resources/**`, `/webjars/**`) via `application-local.yaml`.

To add paths in a deployed environment, override with a comma-separated env var:

```
SECURITY_PERMIT_PATHS_0=/actuator/health/**
SECURITY_PERMIT_PATHS_1=/actuator/info
SECURITY_PERMIT_PATHS_2=/api-docs/**
```

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
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/portal_backend?sslmode=require
  SPRING_DATASOURCE_USERNAME: portal_user
  SPRING_DATASOURCE_PASSWORD: <secret>

  # Credential Encryption (shared with config-adapter)
  CIVITAS_MASTER_KEY: <hex-encoded 256-bit key>

  # Keycloak
  KEYCLOAK_AUTH_SERVER_URL: https://keycloak.example.com
  KEYCLOAK_ISSUER_URI: https://keycloak.example.com
  KEYCLOAK_REALM: civitas-core
  KEYCLOAK_TARGET_REALM: civitas-core

  # Kafka
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  KAFKA_ENABLED: "true"

  # Model Atlas
  MODEL_ATLAS_BASE_URL: http://model-atlas:8080
  MODEL_ATLAS_SCOPE: default
  MODEL_ATLAS_STAGE: draft

  # Optional
  APP_URL: https://api.example.com
  SERVER_PORT: "8089"
```
