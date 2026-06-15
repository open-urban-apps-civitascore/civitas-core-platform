# Portal Backend – Deployment Configuration

---

## Table of Contents

1. [Required for Production](#1-required-for-production)
   - [Database (PostgreSQL)](#11-database-postgresql)
   - [Keycloak / Security](#12-keycloak--security)
   - [Kafka](#13-kafka)
   - [Model Atlas](#14-model-atlas)
   - [Data-Plane Base URL](#15-data-plane-base-url)
2. [Optional / Tuning](#2-optional--tuning)
   - [Server](#21-server)
   - [Event Publishing & Config-Adapter](#22-event-publishing--config-adapter)
   - [Kafka Producer Tuning](#23-kafka-producer-tuning)
   - [Kafka Consumer](#24-kafka-consumer)
   - [Logging](#25-logging)
   - [OpenAPI / Swagger UI](#26-openapi--swagger-ui)
   - [Security Permit Paths](#27-security-permit-paths)
3. [Seed Data Profile (init)](#3-seed-data-profile-init)
4. [Local Development Only](#4-local-development-only)
   - [Local Defaults](#41-local-defaults)
5. [Spring Profiles](#5-spring-profiles)
6. [docker-compose Example](#6-docker-compose-example)

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
| `KAFKA_ENABLED` | `false` | **Must be `true`.** Enables the Kafka CloudEvent publisher. Without it, events are only logged, never sent. |
| `KAFKA_RESULT_TOPIC` | `de.civitascore.config.results` | Topic on which config-adapter publishes processing results |

---

### 1.4 Model Atlas

| Property / Env Var | Default                   | Description |
|---|---------------------------|---|
| `MODEL_ATLAS_BASEURL` | `http://model-atlas:8080` | Model Atlas base URL |
| `MODEL_ATLAS_SCOPE` | `civitas`                 | Scope for requests |
| `MODEL_ATLAS_STAGE` | `draft`                   | Stage for requests |

---

### 1.5 Data-Plane Base URL

| Property / Env Var | Example Value | Description |
|---|---|---|
| `CIVITAS_API_BASE_URL` | `https://api.core.civitasconnect.digital` | Public data-plane base URL used to build the per-named-API `previewUrl` returned on GET `/datasets/{id}`. MUST point at the data-plane host (the host APISIX exposes for `/v1/datasets/{id}/{slug}`), not the management host. Fully-qualified HTTPS URL with no path and no trailing slash. No default — startup fails with a `ConstraintViolationException` if missing or malformed. |

> Validation rejects: missing/empty value, non-`https` scheme, paths, trailing slashes (e.g. `https://api.example.com/` or `https://api.example.com/v1`).

---

## 2. Optional / Tuning

### 2.1 Server

| Property / Env Var | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8089` | HTTP port |
| `SERVER_SERVLET_CONTEXT_PATH` | `/v1` | API path prefix |
| `SERVER_MAX_HTTP_REQUEST_HEADER_SIZE` | `32KB` | Max HTTP request header size — increase if users belong to many dataspaces (large `X-Allowed-Scope-Ids` header from OPA) |

---

### 2.2 Event Publishing & Config-Adapter

| Property | Default | Description |
|---|---|---|
| `EVENT_CONFIG_ADAPTER_TIMEOUT_SECONDS` | `10` | Seconds to wait for config-adapter to reply before throwing `ExternalSystemTimeoutException` |
| `KAFKA_RESULT_TIMEOUT_MS` | `30000` | How long (ms) the CloudEvent publisher waits for a config-adapter result |
| `KAFKA_ACKS` | `all` | Producer ack mode for the CloudEvent publisher |
| `KAFKA_RETRIES` | `3` | Retry count for the CloudEvent publisher |

---

### 2.3 Kafka Producer Tuning

Defaults are tuned for production. Only adjust for specific throughput/latency requirements.

| Property | Default | Description |
|---|---|---|
| `SPRING_KAFKA_PRODUCER_ACKS` | `all` | All in-sync replicas must acknowledge |
| `SPRING_KAFKA_PRODUCER_RETRIES` | `2147483647` | Unlimited retries |
| `SPRING_KAFKA_PRODUCER_BATCH_SIZE` | `16384` | Batch size in bytes |
| `SPRING_KAFKA_PRODUCER_BUFFER_MEMORY` | `33554432` | Producer buffer in bytes |
| `SPRING_KAFKA_PRODUCER_PROPERTIES_ENABLE_IDEMPOTENCE` | `true` | Exactly-once delivery per partition |
| `SPRING_KAFKA_PRODUCER_PROPERTIES_MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION` | `5` | Max unacknowledged in-flight requests |
| `SPRING_KAFKA_PRODUCER_PROPERTIES_COMPRESSION_TYPE` | `snappy` | Compression codec. **`snappy` requires glibc** — on Alpine/musl images (e.g. CI) use `lz4` instead (`SPRING_KAFKA_PRODUCER_PROPERTIES_COMPRESSION_TYPE=lz4`). |
| `SPRING_KAFKA_PRODUCER_PROPERTIES_LINGER_MS` | `10` | Max ms to wait before sending a batch |

---

### 2.4 Kafka Consumer

| Property | Default | Description |
|---|---|---|
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | `civitas-portal-backend` | Consumer group ID |
| `SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `earliest` | Offset reset when no committed offset exists |

---

### 2.5 Logging

| Property | Default | Description |
|---|---|---|
| `LOGGING_LEVEL_ROOT` | `INFO` | Root log level |
| `LOGGING_LEVEL_ORG_HIBERNATE` | `INFO` | Hibernate log level |
| `LOGGING_LEVEL_ORG_FLYWAYDB` | `INFO` | Flyway log level |

> Activate the `debug` profile for verbose SQL and Flyway output.

---

### 2.6 OpenAPI / Swagger UI

| Property / Env Var | Default | Description |
|---|---|---|
| `APP_URL` | `http://localhost:8089` | Server URL shown in Swagger UI — set to the external-facing URL |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `true` | Set to `false` in production to hide API docs |
| `SPRINGDOC_API_DOCS_PATH` | `/v1/api-docs` | OpenAPI JSON path |
| `SPRINGDOC_SWAGGER_UI_PATH` | `/swagger-ui.html` | Swagger UI path |

---

### 2.7 Security Permit Paths

Paths that are accessible **without authentication**. Defined in `application.yaml` via `SECURITY_PERMIT_PATHS` and bound to `SecurityProperties`.

| Property / Env Var | Default | Description |
|---|---|---|
| `SECURITY_PERMIT_PATHS` | `/actuator/health/**`, `/actuator/info` | List of URL patterns permitted without JWT authentication |

Production defaults to actuator endpoints only. The `local` profile adds Swagger/API docs paths (`/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/swagger-resources/**`, `/webjars/**`) via `application-local.yaml`.

To add paths in a deployed environment, override with a comma-separated env var:

```
SECURITY_PERMIT_PATHS_0=/actuator/health/**
SECURITY_PERMIT_PATHS_1=/actuator/info
SECURITY_PERMIT_PATHS_2=/api-docs/**
```

---

## 3. Seed Data Profile (init)

Activate with `SPRING_PROFILES_ACTIVE=postgres,init` (add `local` for local development). Seeds groups and users on startup. Requires `kafka.enabled=true` for Keycloak sync.

Configured via `@ConfigurationProperties(prefix = "init")`. Local defaults are in `application-local.yaml`; production values are provided via ConfigMaps/environment variables.

**Groups** (`init.groups[]`):

| Env Var | Required | Example | Description |
|---|---|---|---|
| `INIT_GROUPS_0_NAME` | yes | `Tenant Admins` | Group name |
| `INIT_GROUPS_0_ROLENAME` | no | `Tenant Admin` | Role assigned to the group. Valid values: `Tenant Admin`, `Data Architect`, `Data Consumer`, `Data Steward`, `Data Owner`, `Data Gatekeeper` |
| `INIT_GROUPS_0_SCOPETYPE` | no | `TENANT` | Assignment scope. Required for DATA roles. Not needed for SYSTEM roles (e.g. Tenant Admin). Values: `TENANT`, `DATASET`, `DATASOURCE`, `DATASTRUCTURE` |
| `INIT_GROUPS_0_DESCRIPTION` | no | `Admin group` | Group description |

**Users** (`init.users[]`):

| Env Var | Required | Example | Description |
|---|---|---|---|
| `INIT_USERS_0_FIRSTNAME` | yes | `Tenant` | — |
| `INIT_USERS_0_LASTNAME` | yes | `Admin` | — |
| `INIT_USERS_0_EMAIL` | yes | `tenant-admin@civitas.local` | Also used as Keycloak username |
| `INIT_USERS_0_PASSWORD` | no | `dev123` | Initial Keycloak password. **Only applied when the `local` profile is active** — ignored in production. |
| `INIT_USERS_0_TITLE` | no | `OTHER` | `MR`, `MS`, or `OTHER` (default: `OTHER`) |
| `INIT_USERS_0_GROUPS_0` | no | `Tenant Admins` | Group name to assign the user to. Increment index for multiple groups. |

Increment the `_0_` index for additional entries (e.g. `INIT_GROUPS_1_NAME`, `INIT_USERS_1_EMAIL`).

> **Note:** Passwords are only set during initial Keycloak sync and only when the `local` profile is active. In production, set passwords directly in Keycloak (admin console or self-service reset).

> **Email:** In the `local` profile, seeded users with passwords are verified automatically (`emailVerified=true`, no required actions). In non-local environments, seeded users receive a Keycloak email to verify their address and set a password — requires SMTP configuration in the Keycloak realm.

---

## 4. Local Development Only

### 4.1 Local Defaults

Set by `application-local.yaml` and `application-postgres.yaml`. Override via environment variables in all deployed environments.

| Env Var | Local Default | Override |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/portal_backend` | `SPRING_DATASOURCE_URL` |
| `SPRING_DATASOURCE_USERNAME` | `admin` | `SPRING_DATASOURCE_USERNAME` |
| `SPRING_DATASOURCE_PASSWORD` | `admin` | `SPRING_DATASOURCE_PASSWORD` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `KEYCLOAK_AUTH_SERVER_URL` | `http://localhost:8080` | `KEYCLOAK_AUTH_SERVER_URL` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8080` | `KEYCLOAK_ISSUER_URI` |
| `KEYCLOAK_REALM` | `civitas-core` | `KEYCLOAK_REALM` |
| `KEYCLOAK_TARGET_REALM` | `civitas-core` | `KEYCLOAK_TARGET_REALM` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | — |
| `SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL` | `true` | — |

---

## 5. Spring Profiles

| Profile | Purpose |
|---|---|
| `local` | Local development defaults |
| `postgres` | PostgreSQL datasource — required for all environments |
| `init` | Seed groups and users on startup |
| `debug` | Verbose SQL/Flyway logging |

> Production: `SPRING_PROFILES_ACTIVE=postgres,init`

---

## 6. docker-compose Example

```yaml
environment:
  SPRING_PROFILES_ACTIVE: postgres,init

  # Database
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/portal_backend?sslmode=require
  SPRING_DATASOURCE_USERNAME: portal_user
  SPRING_DATASOURCE_PASSWORD: <secret>

  # Credential Encryption (shared with config-adapter)
  CIVITAS_MASTER_KEY: <hex-encoded 256-bit key>

  # Data-Plane Base URL (used to build per-named-API previewUrl)
  CIVITAS_API_BASE_URL: https://api.core.civitasconnect.digital

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
