# Assigning Superset Dashboard & Chart Permissions

How to grant/revoke read, write, and public access to a Superset dashboard and its
charts in the Civitas platform. This is an operational guide (which API calls to make);
for the policy internals see [`policy/superset.rego`](../policy/superset.rego) and
[`civitas_security.py`](../../../dev-environment/trino-superset/superset/civitas_security.py).

## Mental model

Superset's own concepts of **ownership** (`dashboard_user` table, "Owners" field in the
UI) and **RBAC roles** (Gamma/Alpha) do **not** govern dashboard/chart access in Civitas.
`CivitasSupersetSecurityManager` overrides every relevant hook to call OPA instead:

- `raise_for_access()` (dashboard read gate) — [civitas_security.py:356](../../../dev-environment/trino-superset/superset/civitas_security.py#L356)
- `can_write_dashboard()` (dashboard edit gate) — [civitas_security.py:415](../../../dev-environment/trino-superset/superset/civitas_security.py#L415)
- `is_editor()` / `raise_for_editorship()` (chart/dataset editorship checks — renamed from `is_owner()`/`raise_for_ownership()` on the Superset version targeted by this branch; those methods were removed upstream in favor of the Subject/editors model) — [civitas_security.py:462](../../../dev-environment/trino-superset/superset/civitas_security.py#L462), [:498](../../../dev-environment/trino-superset/superset/civitas_security.py#L498)
- `resolve_extra_dashboard_ids` / `resolve_extra_chart_ids` (list/lookup visibility, wired via `EXTRA_ACCESS_QUERY_FILTERS` in `superset_config.py`) — [civitas_security.py:69](../../../dev-environment/trino-superset/superset/civitas_security.py#L69), [:119](../../../dev-environment/trino-superset/superset/civitas_security.py#L119)

Being listed as a Superset "editor"/"owner" native to a resource has **no bearing** on
dataset/chart/dashboard permissions — `is_editor()` checks OPA first for every resource
type Civitas manages, and only falls back to Superset's own native editors list for
resource types Civitas doesn't govern at all. `can_write_dashboard()` never consults
native ownership either way. **Ownership does not imply write access; a permission
grant does.** The only account that bypasses OPA entirely is the local admin service
account (`SUPERSET_LOCAL_ADMIN_USER`, default `civitas-config-adapter`).

Every real grant is a **permission scoped to a dataset** (`civitasDatasetId`), not to an
individual dashboard or chart. A dashboard/chart always belongs to exactly one dataset
(stamped into `json_metadata`/`extra` at sync time by `SupersetSagaHandler`), so
dataset-scoped permissions are what gate it.

## Permission reference

| What you want to grant | Permission | Rego rule | Notes |
|---|---|---|---|
| Open a dashboard (see its shell) | `DATASET_DASHBOARD_READ` | `can_read_dashboard` ([superset.rego:311](../policy/superset.rego#L311)) | A `DATASET_DASHBOARD_UPDATE` grant also satisfies this (write implies read). |
| Edit a dashboard's layout/charts | `DATASET_DASHBOARD_UPDATE` | `can_write_dashboard` ([superset.rego:333](../policy/superset.rego#L333)) | |
| See a chart's actual data (payload) | `DATASET_PAYLOAD_READ` | `can_access_schema`/`can_access_datasource` ([superset.rego:220](../policy/superset.rego#L220), [:249](../policy/superset.rego#L249)) | **Independent of dashboard read.** A user can open a dashboard shell without this and see empty/error charts, or vice versa. |
| Edit a chart's query/visualization | `DATASET_UPDATE` | `can_write_chart` ([superset.rego:348](../policy/superset.rego#L348)) | |
| Create/update/delete the Superset dataset object itself | — | `can_write_dataset` ([superset.rego:247](../policy/superset.rego#L247)) | **Not grantable to anyone.** Hard-denied for every real user — only the config-adapter service account can do this (via the local-admin bypass), because dataset provisioning is saga-owned. |
| Appear in `GET /api/v1/chart/` and `GET /api/v1/dashboard/` listings | same as above | `chart_dataset_access`/`dashboard_list_access` ([superset.rego:210](../policy/superset.rego#L210), [:181](../policy/superset.rego#L181)) | Wired via `EXTRA_ACCESS_QUERY_FILTERS`, not a security-manager method override — see `civitas_security.py`'s `resolve_extra_dashboard_ids`/`resolve_extra_chart_ids`. |

Dashboard read/write and chart-data read are genuinely independent axes — granting one
does not imply the other except where noted.

## Scope types

`has_permission()` ([superset.rego:54](../policy/superset.rego#L54)) recognizes:

- **`TENANT`** (or unscoped) — cascades to every dataset. Use for platform-wide roles only.
- **`DATASET`** — the normal case; scoped to one `civitasDatasetId`.
- **`DATAPOOL`** — union-inherited to every dataset in that pool.

`DATASOURCE`-scoped assignments exist in the data model (used elsewhere, e.g. for
`Data Owner`) but are **not evaluated by any Superset rule** — a `DATASOURCE`-scoped
grant will not unlock dashboard/chart access. Scope it at `DATASET` or `DATAPOOL`.

## Public / anonymous access

Public access is **not a permission grant** — it's an attribute of the dataset combined
with a Superset-native flag on the dashboard:

1. The dataset's `openDataAccess` flag must be `true`. Set it via the normal dataset API:
   `PATCH /v1/datasets/{id}` with `{"openDataAccess": true}` (`DataSetInputDTO`).
2. The dashboard must be **published** in Superset (its own native flag, set from the
   Superset UI/API — not a Civitas field).
3. For an anonymous (no-login) visitor: **every** dataset backing the dashboard's charts
   must have `openDataAccess = true` — `can_access_dashboard` ([superset.rego:173](../policy/superset.rego#L173))
   checks the full set, not just the dashboard's own home dataset.
4. For a logged-in user with no `DATASET_DASHBOARD_READ`/`_WRITE` grant, the same
   all-datasets-open check applies as a fallback (`raise_for_access`'s authenticated
   branch, [civitas_security.py:279](../../../dev-environment/trino-superset/superset/civitas_security.py#L279)) — a public dashboard is
   equally visible whether or not the visitor happens to be authenticated.
5. Chart *data* on an open dashboard is still independently re-checked per dataset via
   `can_access_schema`/`can_access_datasource`'s own open-data branch — a dataset that
   isn't open won't render even on an otherwise-public dashboard.

Exposing Superset to genuinely anonymous traffic also requires gateway/Superset config
changes (`AUTH_ROLE_PUBLIC`, APISIX `unauth_action`, identity-header stripping) — see
`portal-backend/DEPLOYMENT.md` §1.8 for the deployment-level checklist. This doc only
covers the per-dataset/per-dashboard authorization side.

## Granting a permission: step by step

Do this through the **portal-frontend UI** (`/roles`, `/groups`) — it exists for exactly
this purpose and covers everything below (`RoleDetails`'s permissions tab for step 1;
a group's Users/Roles tabs, including a `DATASET` scope tab with a dataset picker, for
steps 2–3). The API calls shown here (`/v1/roles`, `/v1/groups`,
`/v1/groups/{id}/assignments`) are what the UI itself calls, useful for scripting or
verification, but prefer the UI for one-off changes. **Never write to the
`portal_backend` database directly** — the API validates input, enforces uniqueness, and
stamps audit columns (`created_by`/`modified_by`) from the caller's JWT; a raw SQL write
does none of that and will silently drift from what the application considers valid
state.

1. **Find or create a Role** with exactly the permission(s) you need.
   `GET /v1/roles?q=<name>` to check for an existing one first — reuse it if the
   permission set matches. Otherwise:
   ```
   POST /v1/roles
   {
     "name": "Dashboard Reader",
     "roleType": "DATA",
     "permissionIds": ["<uuid of DATASET_DASHBOARD_READ>"],
     "readonly": false
   }
   ```
   Look up permission UUIDs via `GET /v1/permissions?q=DATASET_DASHBOARD`.

2. **Find or create a Group** containing exactly the user(s) who should get this grant.
   **Do not reuse a group whose members should end up with different permissions** —
   every assignment on a group applies to every member. If two users need different
   access to the same dataset (e.g. one read, one write), give them separate groups.
   ```
   POST /v1/groups
   { "name": "DS_<name> Dashboard Readers", "memberIds": ["<user-id>"] }
   ```

3. **Assign the role to the group**, scoped to the dataset:
   ```
   PUT /v1/groups/{groupId}/assignments
   [{ "roleId": "<role-id>", "scopeType": "DATASET", "scopeId": "<civitasDatasetId>" }]
   ```
   **This is a full replace, not an add** — `replaceAssignments` (`GroupController`)
   diffs against the group's *entire* current assignment set. If the group already has
   other assignments you want to keep, include them all in the payload; omitting one
   deletes it.

4. **Verify** against OPA directly rather than trusting the UI:
   ```
   curl -X POST http://<opa>:8181/v1/data/civitas/superset/can_read_dashboard \
     -d '{"input": {"user": "<keycloak-sub>", "civitasDatasetId": "<dataset-id>"}}'
   ```
   Substitute `can_write_dashboard`/`can_write_chart`/`can_access_datasource` as needed.

To revoke, remove the relevant entry from the group's assignment set (step 3) — the same
full-replace semantics apply.

## Worked example

Grant one user read-only dashboard access and another write access to the same
dashboard, where both users currently share a group with unrelated dataset permissions
they should keep:

1. Create role `Dashboard Reader` → `DATASET_DASHBOARD_READ`.
2. Create group `DS_X Dashboard Readers`, member = reader user, assign `Dashboard
   Reader` at `DATASET` scope = the dashboard's dataset.
3. Create group `DS_X Dashboard Writers`, member = writer user, assign a role granting
   `DATASET_DASHBOARD_UPDATE` at the same scope.
4. If a write grant already exists on the users' *shared* group, remove it there
   (replacing that group's assignment set with everything except the dashboard-write
   entry) so the reader doesn't inherit write access through the shared group. Confirm
   with whoever owns that group first — replacing its assignment set affects every
   member, and removing a grant is a different kind of change from adding one.
5. Verify with the OPA queries above for both users.
