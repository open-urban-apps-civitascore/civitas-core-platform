# Portal Backend – Deployment Configuration

---

## Table of Contents

1. [Required for Production](#1-required-for-production)
   - [Database (PostgreSQL)](#11-database-postgresql)
   - [Keycloak / Security](#12-keycloak--security)
   - [Kafka](#13-kafka)
   - [Data-Plane Base URL](#15-data-plane-base-url)
   - [Gateway Trust Model (APISIX / OPA)](#16-gateway-trust-model-apisix--opa)
   - [Open Data Access (anonymous pass-through)](#17-open-data-access-anonymous-pass-through)
2. [Optional / Tuning](#2-optional--tuning)
   - [Server](#21-server)
   - [Event Publishing & Config-Adapter](#22-event-publishing--config-adapter)
   - [Kafka Producer Tuning](#23-kafka-producer-tuning)
   - [Kafka Consumer](#24-kafka-consumer)
   - [Logging](#25-logging)
   - [OpenAPI / Swagger UI](#26-openapi--swagger-ui)
   - [Security Permit Paths](#27-security-permit-paths)
   - [Group-Member Backfill (one-shot)](#28-group-member-backfill-one-shot)
   - [Dataset Staging & Release](#29-dataset-staging--release)
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
| `KEYCLOAK_ENFORCE_OTP` | `true` | When `true` (default), newly created users get the `CONFIGURE_TOTP` required action and must set up TOTP on first login. Set to `false` to disable enforced OTP setup. |

---

### 1.3 Kafka

| Property / Env Var | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | *(required)* | Sets both `spring.kafka.bootstrap-servers` and `kafka.bootstrap-servers` — only one env var needed |
| `KAFKA_ENABLED` | `false` | **Must be `true`.** Enables the Kafka CloudEvent publisher. Without it, events are only logged, never sent. |
| `KAFKA_RESULT_TOPIC` | `de.civitascore.config.results` | Topic on which config-adapter publishes processing results |

---

### 1.5 Data-Plane Base URL

| Property / Env Var | Example Value | Description |
|---|---|---|
| `CIVITAS_API_BASE_URL` | `https://api.core.civitasconnect.digital` | Public data-plane base URL used to build the per-named-API `previewUrl` returned on GET `/datasets/{id}`. MUST point at the data-plane host (the host APISIX exposes for `/v1/datasets/{id}/{slug}`), not the management host. Fully-qualified HTTPS URL with no path and no trailing slash. No default — startup fails with a `ConstraintViolationException` if missing or malformed. |

> Validation rejects: missing/empty value, non-`https` scheme, paths, trailing slashes (e.g. `https://api.example.com/` or `https://api.example.com/v1`).

---

### 1.6 Gateway Trust Model (APISIX / OPA)

The backend has **no authorization logic of its own** — it *trusts* two HTTP headers that OPA
sets at the APISIX gateway and that decide which datasets a request may see:

| Header | Meaning |
|---|---|
| `X-Allowed-Scope-Ids` | dataset ids the caller may see (`*` = tenant-wide / unscoped) |
| `X-Allowed-Pool-Ids`  | datapool ids whose datasets the caller may see (datapool-union) |

Because these headers grant data visibility, the deployment **MUST** guarantee they can only ever
originate from OPA — never from a client. That guarantee rests on three infrastructure properties
that live in the **`civitas-core-deployment`** repo (owned by Team 3), **not** in this backend's
container config. They are listed here so the backend's trust assumptions are documented in one
place; the dev-environment equivalents are in `dev-environment/apisix/apisix_conf/apisix.yaml`.

1. **TLS to Keycloak** — the prod `openid-connect` plugin MUST set `ssl_verify: true`
   (the dev config disables it for local HTTP Keycloak only).

2. **Header forward + strip at the gateway (review finding F1).** In the prod APISIX config
   (`components/portal/apisix-plugins.yaml`):
   - `opa.send_headers_upstream` MUST list **both** `X-Allowed-Scope-Ids` and `X-Allowed-Pool-Ids`,
     otherwise OPA's decision is dropped and scope/pool filtering silently does nothing.
   - `proxy-rewrite.headers.remove` MUST strip both client-supplied copies, otherwise a spoofed
     header is trusted (authorization bypass). The strip list and the saga-route caveat (route-level
     `proxy-rewrite` overrides the shared one, so the list must be mirrored via
     `APISIX_PROXY_REWRITE_HEADERS_REMOVE`) are documented in
     `config-adapter/config-adapter-apisix/README.md`.
   - The dev side of this contract is guarded by the automated test
     `TrustedHeaderGatewayContractTest`; there is **no in-repo guard for the prod config** — keep it
     in sync by hand.

3. **Backend network isolation (review finding F2).** The backend `NetworkPolicy` MUST allow
   ingress **only from the APISIX gateway**, not directly from the frontend. The frontend reaches the
   backend exclusively through APISIX (see `portal-frontend/.env.local.template`); a direct
   frontend→backend path would bypass the header strip in (2) and let the frontend spoof the trusted
   headers.

> **Deploy F1 and F2 together.** Tightening the `NetworkPolicy` (F2) before the gateway forwards the
> headers (F1), or vice versa, either locks out the frontend or opens the spoofing window. Roll them
> out as one coordinated change.

---

### 1.7 Open Data Access (anonymous pass-through)

"Open data" datasets (`Dataset.openDataAccess = true`) allow **anonymous** read access to their
**payload** (e.g. STA/FROST, OWS/GeoServer). This is decided **centrally by OPA at request time** —
there is no per-route bypass and the FROST project is never made public. For OPA to make that
decision, anonymous requests must *reach* OPA instead of being rejected at the gateway, which changes
the prod APISIX `openid-connect` configuration.

> **No portal-backend change is required for this feature.** The backend has no role here:
> `openDataAccess` is persisted on the dataset and read by **OPA** from the AuthZ Repository. There is
> **no new env var** on this service. The work below lives entirely in the **`civitas-core-deployment`**
> repo (prod APISIX config, owned by Team 3); the dev equivalents are in
> `dev-environment/apisix/apisix_conf/apisix.yaml` and `dev-environment/apisix/seed-routes.sh`.

The shared dataset-route plugin config (OIDC + OPA) MUST be set up as follows:

1. **Let anonymous requests pass to OPA.** The `openid-connect` plugin MUST run with:

   | Setting | Value | Why |
   |---|---|---|
   | `unauth_action` | `"pass"` | An unauthenticated request continues to OPA instead of a 401 at the gateway. OPA then grants or denies per the dataset's `openDataAccess` flag. |
   | `bearer_only` | `false` | Required by APISIX once `unauth_action: pass` is used. A present bearer token is still validated and its claims still forwarded as `X-Userinfo`. |
   | `access_token_in_authorization_header` | `true` | Keeps bearer-token auth working for authenticated callers under `bearer_only: false`. |

2. **`session.secret` is now a real credential — inject a strong one.** With `bearer_only: false`,
   `openid-connect` processes session cookies, so `session.secret` is an **authentication secret**, not
   an inert schema value: anyone who knows it can mint session state the gateway trusts. It MUST be a
   strong, **injected** value (e.g. from a Kubernetes Secret) and MUST NOT be a known, shared, or
   committed constant. (The dev seeding script generates a fresh **random** secret per run for exactly
   this reason; never copy a dev value into prod.)

3. **Strip client-supplied identity headers at the gateway.** Because anonymous requests now reach
   OPA, and OPA derives identity from `X-Userinfo`, a `serverless-pre-function` MUST run in the
   **rewrite phase, before `openid-connect`**, and clear any client-supplied `X-Userinfo`,
   `X-Access-Token`, and `X-Id-Token`. Without it a client can forge an identity (authorization
   bypass). The `serverless-pre-function` plugin MUST also be present in the APISIX `plugins`
   allowlist. This is the identity-header analogue of the `X-Allowed-Scope-Ids`/`X-Allowed-Pool-Ids`
   strip in [§1.6](#16-gateway-trust-model-apisix--opa) point 2, and the same saga-route caveat applies
   (a route-level `proxy-rewrite` overrides the shared one), so the strip must hold on the
   saga-created `/v1/datasets/{id}/{slug}` routes as well — keep it on the shared plugin config that
   every route references.

> **Deploy points 1–3 together.** Enabling `unauth_action: pass` (point 1) without the identity-header
> strip (point 3) opens an identity-spoofing window the moment anonymous requests can reach OPA. Roll
> them out as one coordinated change.

> **Scope:** open data is **payload-only**. Management/discovery endpoints
> (`GET /v1/datasets/{id}`, `GET /v1/datasets/{id}/apis`) stay authenticated at both OPA and this
> backend — do not add them to any anonymous allowlist.

The dev side of this contract is guarded by the Bruno API tests `9c2`/`9c3`/`9c4` (forged
`X-Userinfo` and forged session cookie → 401), `9g`/`9g2`/`9g3` (anonymous STA payload → 200, anonymous
write/discovery → 401), and `9g4` (anonymous OWS/GeoServer payload → 200, proving open data is
universal across routable backends); there is **no in-repo guard for the prod APISIX config** — keep
it in sync by hand.

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

```bash
SECURITY_PERMIT_PATHS_0=/actuator/health/**
SECURITY_PERMIT_PATHS_1=/actuator/info
SECURITY_PERMIT_PATHS_2=/api-docs/**
```

---

### 2.8 Group-Member Backfill (one-shot)

One-time migration switch that reconciles the members of already-synced groups (those with a Keycloak `externalId`) into Keycloak, covering groups whose `group_members` rows predate the group-side member sync. On startup, after the catch-up sync, it re-emits `GROUP_UPDATED` per already-synced group; the config-adapter reconciles membership diff-based.

| Property / Env Var | Default | Description |
|---|---|---|
| `KEYCLOAK_GROUP_MEMBER_BACKFILL` | `false` | When `true`, reconcile already-synced groups' members into Keycloak on startup |

Enable for a single rollout deploy, then check the completion log — `Group-member backfill completed: N succeeded, M failed, K skipped (of T candidates)` — and re-run while the flag is on if `M > 0` (failures are also logged individually at `ERROR`). Once it reports `0 failed`, set the flag back to `false`. The reconcile is idempotent; there is no run-once marker, so the flag must be turned off after rollout.

---

### 2.9 Dataset Staging & Release

Before a dataset is released, the backend walks out from each of its pipelines over the references the model registry recorded and refuses the transition while an artifact the flow reaches cannot carry a release. The walk follows a bounded number of hops.

| Property / Env Var | Default | Description |
|---|---|---|
| `DATASET_CLOSUREVALIDATION_MAXDEPTH` | `10` | Reference hops the walk follows out from a pipeline. A flow reaching further is **refused**, not passed unchecked — everything past the bound goes unexamined |

Raise it only when real models legitimately nest deeper: observed flows reach two to three hops, so the default leaves roughly threefold headroom. Lowering it below what a deployment's models need makes those datasets unreleasable, with a 422 naming the offending pipelines and the log recording that the bound cut the walk short. The walk visits each artifact once and terminates on reference cycles, but resolves every unpinned reference against the registry, so raising the bound does add reads.

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
| `KEYCLOAK_ENFORCE_OTP` | `true` | `KEYCLOAK_ENFORCE_OTP` |
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
  KEYCLOAK_ENFORCE_OTP: "true"

  # Kafka
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  KAFKA_ENABLED: "true"

  # Optional
  APP_URL: https://api.example.com
  SERVER_PORT: "8089"
```
