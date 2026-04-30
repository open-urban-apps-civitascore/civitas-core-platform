# Portal Backend API — Bruno Test Collection

API test collection for the Portal Backend, built with [Bruno](https://www.usebruno.com/).

## Prerequisites

| Service          | URL                        | Required for         |
|------------------|----------------------------|----------------------|
| Portal Backend   | `http://localhost:8089`     | All tests            |
| Keycloak         | `http://localhost:8080`     | Authentication       |
| PostgreSQL       | `localhost:5432`            | Backend persistence  |
| Model Atlas      | `http://localhost:8086`     | DataStructure release/version patch |

Start the backend with:

```bash
cd portal-backend
./gradlew bootRun --args='--spring.profiles.active=local,local-init'
```

## Running Tests

### Full suite (CLI)

```bash
cd api/portal-backend/bruno-api
npx @usebruno/cli run --env local-direct
```

This runs all folders alphabetically: `00-setup` → CRUD folders → `dataset-saga-workflow` → `zz-teardown`.

### Single CRUD folder

CRUD folders (`assignments`, `datasets`, `datasources`, etc.) depend on variables set by `00-setup`. Run setup first:

```bash
npx @usebruno/cli run 00-setup --env local-direct
npx @usebruno/cli run datasets --env local-direct
```

### Saga workflow (standalone)

The saga workflow is self-contained — it includes its own find-first lookup files that resolve all required entity IDs:

```bash
npx @usebruno/cli run dataset-saga-workflow --env local-direct
```

### Bruno GUI

Open the collection in Bruno, select the `local-direct` environment, and send individual requests. For CRUD requests, run the `00-setup` folder first (right-click → "Run Folder") to populate variables.

## Folder Structure

```
bruno-api/
├── 00-setup/                    # Find-or-create test entities
│   ├── 00-find-existing-*.bru   # GET lookups (idempotent variable init)
│   ├── 01..07-create-*.bru      # POST creates (tolerate 409)
│   └── 07-fetch-permission-id   # Lookup for permission tests
├── assignments/                 # Assignment CRUD
├── authorities/                 # Authority endpoints
├── datasets/                    # DataSet CRUD + lifecycle
├── datasources/                 # DataSource CRUD + lifecycle
├── datastructures/              # DataStructure + Version CRUD + lifecycle
├── groups/                      # Group CRUD
├── permissions/                 # Permission read endpoints
├── pipelines/                   # Pipeline CRUD
├── roles/                       # Role CRUD
├── users/                       # User CRUD
├── dataset-saga-workflow/       # End-to-end saga test (standalone)
│   ├── 0a..0f-find-saga-*.bru   # Find existing entities from prior runs
│   ├── 1..6-create-*.bru        # Create datasource/group/role/dataset/pipeline/assignment
│   ├── 7-stage / 8-release      # Lifecycle transitions (triggers async saga)
│   ├── 9..10-verify-*.bru       # Verify state after saga
│   ├── 11-unrelease / 12-unstage # Reverse lifecycle
│   └── 13..17-cleanup-*.bru     # Delete all created entities
├── zz-teardown/                 # Delete setup entities
├── environments/
│   └── local-direct.bru         # localhost:8089 (direct, no gateway)
├── collection.bru               # OAuth2 auth config
└── bruno.json                   # Collection metadata
```

## Idempotent / Repeatable Runs

All tests are designed to pass on **repeated runs** without manual cleanup. This is achieved through two patterns:

### 1. Find-first lookups

Before creating entities, lookup files search for existing ones by name. If found, the ID is stored in a variable and the subsequent `POST` simply gets a `409` (which is tolerated). This avoids the problem of `POST` returning `409` without an ID in the response body.

### 2. Multi-status assertions

Tests accept multiple HTTP status codes, each documented with inline comments:

```javascript
// 201=created, 409=already exists from previous run
expect(status === 201 || status === 409).to.be.true;

// 200=released, 400=not in READY state (already released)
expect(status === 200 || status === 400).to.be.true;

// 204=deleted, 404=already gone, 400=not in deletable state
expect(status === 204 || status === 404 || status === 400).to.be.true;
```

This is **not** ignoring errors — each accepted code represents a valid state the entity can be in during a re-run. The test verifies that the API responds correctly for the current state, rather than assuming a clean database.

## CI / GitLab Pipeline

The `api-test-backend` job in `.gitlab/ci/backend.yml` runs the Bruno tests automatically:

- **main / develop**: runs automatically after the backend JAR is built
- **Merge Requests**: manual trigger (when `portal-backend/` or `bruno-api/` files change)

The job spins up a self-contained Docker Compose stack (`dev-environment/ci/docker-compose.api-test.yml`) with PostgreSQL, Keycloak, Kafka, and the Portal Backend, then runs the full Bruno collection against it using the `ci` environment.

## Environments

### `local-direct` (local development)

Connects directly to the backend on the host machine (port 8089), bypassing the APISIX gateway.

| Variable       | Value                              |
|----------------|------------------------------------|
| `baseUrl`      | `http://localhost:8089/v1`         |
| `keycloakUrl`  | `http://localhost:8080`            |
| `realm`        | `civitas-core`                     |
| `username`     | `dev@civitas.local`                |
| `password`     | `dev123`                           |

### `ci` (GitLab CI)

Uses Docker Compose service names to reach containers within the CI network.

| Variable       | Value                              |
|----------------|------------------------------------|
| `baseUrl`      | `http://portal-backend:8089/v1`   |
| `keycloakUrl`  | `http://keycloak:8080`            |
| `realm`        | `civitas-core`                     |
| `username`     | `dev@civitas.local`                |
| `password`     | `dev123`                           |

Authentication is handled automatically via OAuth2 password grant (configured in `collection.bru`).
