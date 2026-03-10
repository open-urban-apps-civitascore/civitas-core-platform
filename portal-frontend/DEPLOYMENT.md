# Portal Frontend – Deployment Configuration

---

## Table of Contents

1. [Required for Production](#1-required-for-production)
   - [Authentication (NextAuth / Auth.js)](#11-authentication-nextauth--authjs)
   - [Keycloak](#12-keycloak)
   - [API Gateway (APISIX)](#13-api-gateway-apisix)
2. [Optional / Tuning](#2-optional--tuning)
   - [Server](#21-server)
   - [Logging](#22-logging)
   - [Tenant Branding](#23-tenant-branding)
3. [Local Development Only](#3-local-development-only)
   - [JSON Server (Mock Backend)](#31-json-server-mock-backend)
   - [E2E Test Configuration](#32-e2e-test-configuration)
4. [Build-time vs Runtime Variables](#4-build-time-vs-runtime-variables)
5. [docker-compose Example](#5-docker-compose-example)

---

## 1. Required for Production

### 1.1 Authentication (NextAuth / Auth.js)

| Env Var | Example Value | Description |
|---|---|---|
| `NEXTAUTH_SECRET` | *(generate with `openssl rand -base64 32`)* | Secret used to sign/verify the encrypted NextAuth session cookie. Must be identical across all instances. |
| `NEXT_SERVER_URL` | `https://portal.example.com` | Base URL of the Next.js application itself. Used as the `baseURL` for client-side API calls (Axios), routing them through the BFF proxy at `/api/[...path]`. |

> `NEXT_AUTH_URL` appears in `.env.local.template` but is **not referenced** in source code — it is a legacy placeholder. The actually-used variable is `NEXT_SERVER_URL`.

---

### 1.2 Keycloak

| Env Var | Example Value | Description |
|---|---|---|
| `KEYCLOAK_CLIENT_ID` | `portal-frontend` | OAuth2 client ID registered in Keycloak. Used for login, logout, and token refresh. |
| `KEYCLOAK_CLIENT_SECRET` | *(from Keycloak admin console)* | OAuth2 client secret. Used for authorization code exchange and token refresh. **Never expose to the browser.** |
| `KEYCLOAK_ISSUER` | `https://keycloak.example.com/realms/civitas-core` | Full Keycloak realm issuer URL (including `/realms/<realm>`). Used as the OIDC discovery base and to construct token/logout endpoints. Must match the `iss` claim in JWTs. |

---

### 1.3 API Gateway (APISIX)

| Env Var | Example Value | Description |
|---|---|---|
| `API_BASE_URL` | `http://apisix` | Base URL of the APISIX gateway (host only, no port, no path). Combined with `API_PORT` to form `API_BASE_URL:API_PORT/v1`. In Kubernetes, point to the APISIX service. |
| `API_PORT` | `9080` | Port of the APISIX gateway. |

---

## 2. Optional / Tuning

### 2.1 Server

| Env Var | Default | Description |
|---|---|---|
| `NODE_ENV` | `production` (in Dockerfile) | Standard Node.js environment flag. Controls: Pino log transport (pretty-print in `development`, JSON in `production`); CSP policy strictness in middleware. |
| `PORT` | `80` (in Dockerfile) | Port Next.js listens on inside the container. |

---

### 2.2 Logging

| Env Var | Default | Description |
|---|---|---|
| `LOG_LEVEL` | `info` | Pino log level. Valid values: `error`, `warn`, `info`, `debug`. Set to `debug` for verbose BFF proxy logging. |

---

### 2.3 Tenant Branding

| Env Var | Default | Description |
|---|---|---|
| `NEXT_PUBLIC_TENANT_NAME` | `Mandanten-Name` | Tenant display name shown in the sidebar header. **Build-time only** — must be set when `pnpm build` runs (see [section 4](#4-build-time-vs-runtime-variables)). |

---

## 3. Local Development Only

### 3.1 JSON Server (Mock Backend)

> These variables and the JSON Server itself are **temporary** and will be removed once the real backend is fully integrated.

| Env Var | Default | Description |
|---|---|---|
| `JSON_SERVER_HOST` | `http://localhost` | Host URL of the local JSON Server mock. |
| `JSON_SERVER_PORT` | `3001` | Port for the JSON Server mock. |
| `NEXT_PUBLIC_JSON_SERVER_HOST` | `http://localhost` | Browser-accessible JSON Server host (used by Playwright/Vitest only). |
| `NEXT_PUBLIC_JSON_SERVER_PORT` | `3001` | Browser-accessible JSON Server port (used by Playwright/Vitest only). |

---

### 3.2 E2E Test Configuration

Required only when running `pnpm test:e2e`. Set in `.env.local`.

| Env Var | Description |
|---|---|
| `E2E_USERNAME` | Keycloak username of the test user |
| `E2E_PASSWORD` | Keycloak password of the test user |
| `E2E_EMAIL` | Email address of the test user |
| `E2E_FIRSTNAME` | First name of the test user |
| `E2E_LASTNAME` | Last name of the test user |
| `E2E_TEST_ENV` | Target environment (`dev` by default). Warning is logged if not set. |

> The `CI` environment variable (set automatically by GitLab CI) changes Playwright behavior: forbids `test.only`, reduces retries, limits workers to 1, and adds Firefox/WebKit to the test matrix.

---

## 4. Build-time vs Runtime Variables

Next.js treats environment variables differently based on their prefix:

| Prefix | Inlined at | Can change without rebuild? |
|---|---|---|
| `NEXT_PUBLIC_*` | **Build time** (`pnpm build`) | No — baked into the client JS bundle |
| All others | **Runtime** (server-side only) | Yes — inject via `docker run -e` or k8s `env:` |

**Implication:** `NEXT_PUBLIC_TENANT_NAME` must be known at build time. All secrets (`NEXTAUTH_SECRET`, `KEYCLOAK_CLIENT_SECRET`) and API coordinates (`API_BASE_URL`, `API_PORT`) are runtime-only and never sent to the browser.

---

## 5. docker-compose Example

```yaml
environment:
  # Authentication
  NEXTAUTH_SECRET: <generate with openssl rand -base64 32>
  NEXT_SERVER_URL: https://portal.example.com

  # Keycloak
  KEYCLOAK_CLIENT_ID: portal-frontend
  KEYCLOAK_CLIENT_SECRET: <from Keycloak admin console>
  KEYCLOAK_ISSUER: https://keycloak.example.com/realms/civitas-core

  # API Gateway
  API_BASE_URL: http://apisix
  API_PORT: "9080"

  # Optional
  LOG_LEVEL: info
  PORT: "80"
```

> `NEXT_PUBLIC_TENANT_NAME` is not listed here because it must be set at **build time**, not at container runtime. Pass it as a build arg or set it in the CI pipeline before running `pnpm build`.
