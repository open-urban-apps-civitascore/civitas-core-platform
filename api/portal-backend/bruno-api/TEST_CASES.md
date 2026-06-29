# Data Management Portal – Manual Test Cases

## Prerequisites

### Starting the Development Environment

The application runs inside a local dev environment managed by a startup script.
Start it before running any test cases:

```bash
~/git/civitas-core-platform/dev-environment/start-portal-dev.sh
```

The script starts all required infrastructure (Keycloak, PostgreSQL, Kafka, APISIX, OPA, FROST)
and optionally builds and starts the portal backend and frontend.
Refer to the script's `--help` output for available options (e.g. `--authz`, `--backend`, `--frontend`).

Once running, the relevant service endpoints are:

| Service | URL |
|---|---|
| Portal Backend | `http://localhost:8089` |
| Keycloak Admin | `http://localhost:8080` |
| APISIX Gateway | `http://localhost:9080` |
| Portal Frontend | `http://localhost:3000` |

### Test Variables

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8089` |
| `baseUrlKeycloak` | `http://localhost:8080` |
| Keycloak realm | `civitas-core` |
| Test user | `dev@civitas.local` / `dev123` |
| Client ID | `portal-frontend` |

Authentication is handled automatically via OAuth2 at the Bruno collection level (`collection.bru`).
Bruno fetches and injects the Bearer token for every request.

### System Impact Tables

Every test case that creates, updates, or deletes data ends with a **System Impact** table.
The columns are:

| Column | Description |
|---|---|
| System | The component where a change is expected |
| Expected Change | What should have changed after the request |

The three systems covered are:
- **Backend** — portal-backend service, persists to PostgreSQL (`portal_backend` DB)
- **Config Adapter** — listens to Kafka events and forwards changes to external systems
- **External System** — the specific downstream system (Keycloak, APISIX, FROST Server), or `—` if not applicable

---

## Bruno Collections

Test case documentation is embedded in each `.bru` file's `docs` block (visible in Bruno's **Docs** tab).

| Collection | Folder | Operations |
|---|---|---|
| Authorities | `authorities/` | Get All Authorities |
| Users | `users/` | CRUD + My Profile + Check Mailpit Emails |
| Groups | `groups/` | CRUD + Replace Assignments |
| Roles | `roles/` | CRUD |
| Permissions | `permissions/` | Read-only |
| Assignments | `assignments/` | Create / Create Scoped / Read / Delete |
| DataSources | `datasources/` | CRUD + Release / Unrelease + Released Meta + Assignments |
| DataSets | `datasets/` | CRUD + Stage / Unstage + Release / Unrelease + Released Meta + Assignments |
| Pipelines | `pipelines/` | CRUD (nested under DataSet) |
| DataStructures | `datastructures/` | CRUD + Release / Unrelease + Released Meta + Assignments |
| DataStructure Versions | `datastructures/versions/` | CRUD + Release / Unrelease + Released Meta |
