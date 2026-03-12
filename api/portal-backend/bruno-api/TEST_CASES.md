# Data Management Portal – Manual Test Cases

## Prerequisites

### Starting the Development Environment

The application runs inside a local dev environment managed by a startup script.
Start it before running any test cases:

```bash
~/git/civitas-core-platform/dev-environment/start-portal-dev.sh
```

The script starts all required infrastructure (Keycloak, PostgreSQL, Kafka, APISIX, OPA, FROST, Model Atlas)
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
| Verified | Checkbox — tick after manual confirmation |

The three systems covered are:
- **Backend** — portal-backend service, persists to PostgreSQL (`portal_backend` DB)
- **Config Adapter** — listens to Kafka events and forwards changes to external systems
- **External System** — the specific downstream system (Keycloak, APISIX, FROST Server, Model Atlas), or `—` if not applicable

---

## Test Case Files

| Resource | File | Operations |
|---|---|---|
| Auth | [authorities/AUTH.md](authorities/AUTH.md) | Get All Authorities |
| Users | [users/USERS.md](users/USERS.md) | CRUD + My Profile |
| Groups | [groups/GROUPS.md](groups/GROUPS.md) | CRUD + Replace Assignments |
| Roles | [roles/ROLES.md](roles/ROLES.md) | CRUD |
| Permissions | [permissions/PERMISSIONS.md](permissions/PERMISSIONS.md) | Read-only |
| Assignments | [assignments/ASSIGNMENTS.md](assignments/ASSIGNMENTS.md) | Create / Create Scoped / Read / Delete |
| DataSources | [datasources/DATASOURCES.md](datasources/DATASOURCES.md) | CRUD + Publish / Unpublish + Published Meta + Assignments |
| DataSets | [datasets/DATASETS.md](datasets/DATASETS.md) | CRUD + Publish + Published Meta + Assignments |
| Pipelines | [pipelines/PIPELINES.md](pipelines/PIPELINES.md) | CRUD (nested under DataSet) |
| DataStructures | [datastructures/DATASTRUCTURES.md](datastructures/DATASTRUCTURES.md) | CRUD + Publish / Unpublish + Published Meta + Assignments |
| DataStructure Versions | [datastructures/versions/DATASTRUCTURE-VERSIONS.md](datastructures/versions/DATASTRUCTURE-VERSIONS.md) | CRUD + Publish / Unpublish + Published Meta |
