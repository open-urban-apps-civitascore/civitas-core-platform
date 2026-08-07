import json
import logging
import os
import re
from typing import Any

from flask import current_app, g, request
from flask_babel import lazy_gettext as _
from superset.errors import ErrorLevel, SupersetError, SupersetErrorType
from superset.exceptions import SupersetSecurityException
from superset.security.manager import SupersetSecurityManager

from opa_client import OpaClient

logger = logging.getLogger(__name__)

_opa = OpaClient(os.getenv("OPA_URL", "http://civitas-opa:8181"))

# Username of the local service-account that bypasses OPA (e.g. the config-adapter admin).
# Set SUPERSET_LOCAL_ADMIN_USER="" to disable the bypass entirely.
_LOCAL_ADMIN_USER = os.getenv("SUPERSET_LOCAL_ADMIN_USER", "civitas-config-adapter")

# Superset display name of the Trino database connection. Used to build schema_perm strings
# in the format "[DatabaseName].[schema]" that match what Superset stores in SqlaTable.schema_perm.
_TRINO_DB_NAME = os.getenv("SUPERSET_TRINO_DATABASE_NAME", "Trino")

_NON_WORKSPACE_CHAR = re.compile(r"[^a-z0-9_]")


def _workspace_name(dataset_id: str) -> str:
    """Mirrors config-adapter's WorkspaceNames.fromDatasetId (Java): lowercases the id,
    replaces every character that is not a-z, 0-9 or _ with _, and prefixes with "ds_"
    when the result would start with a digit. This must stay in lockstep with the Java
    implementation — it derives the same PostGIS/GeoServer schema name that Superset
    stores as SqlaTable.schema, which the schema_access permission strings built here
    must match.
    """
    normalized = _NON_WORKSPACE_CHAR.sub("_", dataset_id.lower())
    return f"ds_{normalized}" if normalized[:1].isdigit() else normalized


def _civitas_id_from_extra(extra: str | None) -> str | None:
    try:
        return (json.loads(extra or "{}") or {}).get("civitasDatasetId")
    except Exception:
        return None


def _civitas_dataset_id(datasource) -> str | None:
    civitas_id = _civitas_id_from_extra(getattr(datasource, "extra", None))
    if civitas_id is None and getattr(datasource, "extra", None):
        logger.warning("Failed to parse civitasDatasetId from datasource.extra")
    return civitas_id


def _sub_for_user_id(user_id: int) -> str | None:
    """Resolve a numeric FAB user id (what EXTRA_ACCESS_QUERY_FILTERS callables receive) to the
    Civitas Keycloak sub. Safe to call outside of any g.user/JWT request context — unlike _sub()
    on the security manager, this doesn't need the current request, since Superset's own filter
    plumbing (get_user_id()) already resolved the caller before invoking these callables.
    """
    from superset import db
    from flask_appbuilder.security.sqla.models import User

    user = db.session.query(User).get(user_id)
    return getattr(user, "username", None) if user else None


def resolve_extra_dashboard_ids(user_id: int):
    """EXTRA_ACCESS_QUERY_FILTERS["dashboards"]: dashboard ids to OR into DashboardAccessFilter's
    native list criteria. Native ownership/published+chart-access never surface a
    Civitas-provisioned dashboard for a real reader: SupersetSagaHandler leaves every dashboard
    unpublished and owned only by the config-adapter service account, and an empty dashboard (no
    slices yet) can never satisfy the native chart-access join regardless of permissions.

    Gathering the candidate {id, published} pairs is the only part that has to happen here —
    nothing else knows which dashboards exist in Superset or their published state. Civitas
    dataset resolution and the actual read/write/published decision both happen inside OPA's
    dashboard_list_access (superset.rego), via ONE call carrying the whole candidate list —
    Superset never calls authz/repository directly, only OPA (mirrors main.rego's
    X-Allowed-Scope-Ids pattern: one call, not one per dashboard).

    Only invoked for an authenticated caller (Superset's own filter code only calls this when
    get_user_id() is truthy) — there is deliberately no anonymous-listing branch here; per
    portal-backend/DEPLOYMENT.md §1.8, the dashboard list endpoint was never treated as the public
    catalog for open-data dashboards. Anonymous single-dashboard access is independently gated by
    raise_for_access's own open-data branch below, unaffected by this filter.
    """
    from superset import db
    from superset.models.dashboard import Dashboard

    empty = db.session.query(Dashboard.id).filter(False)
    sub = _sub_for_user_id(user_id)
    if not sub:
        return empty

    rows = list(db.session.query(Dashboard.id, Dashboard.published))
    if not rows:
        return empty
    id_by_external_id = {str(dashboard_id): dashboard_id for dashboard_id, _ in rows}
    dashboards_payload = [
        {"externalDashboardId": str(dashboard_id), "published": bool(published)}
        for dashboard_id, published in rows
    ]

    result = _opa.query(
        "civitas/superset", "dashboard_list_access", {"user": sub, "dashboards": dashboards_payload}
    )
    allowed_external_ids = (result or {}).get("allowedDashboardIds", [])
    ids = [
        id_by_external_id[external_id]
        for external_id in allowed_external_ids
        if external_id in id_by_external_id
    ]
    logger.warning("resolve_extra_dashboard_ids: sub=%s → ids=%s", sub, ids)
    return db.session.query(Dashboard.id).filter(Dashboard.id.in_(ids)) if ids else empty


def resolve_extra_chart_ids(user_id: int):
    """EXTRA_ACCESS_QUERY_FILTERS["charts"]: chart ids to OR into ChartFilter's native list
    criteria. Charts are always table-backed (the Druid connector is gone) and always carry a
    datasource_id, so — unlike dashboards — visibility can be derived purely from the chart's
    own dataset, no fetcher round-trip needed: civitasDatasetId already lives in
    SqlaTable.extra, in Superset's own DB.

    One OPA call per request either way, keyed by the deduplicated set of dataset ids among all
    charts (bounded by dataset count, not chart count) rather than one call per chart.
    """
    from superset import db
    from superset.connectors.sqla.models import SqlaTable
    from superset.models.slice import Slice

    empty = db.session.query(Slice.id).filter(False)
    sub = _sub_for_user_id(user_id)
    if not sub:
        return empty

    rows = list(
        db.session.query(Slice.id, Slice.datasource_id).filter(Slice.datasource_type == "table")
    )
    if not rows:
        return empty

    dataset_civitas_ids = {
        sqla_id: civitas_id
        for sqla_id, extra in db.session.query(SqlaTable.id, SqlaTable.extra)
        if (civitas_id := _civitas_id_from_extra(extra))
    }
    candidate_civitas_ids = sorted({
        dataset_civitas_ids[ds_id] for _, ds_id in rows if ds_id in dataset_civitas_ids
    })
    if not candidate_civitas_ids:
        return empty

    result = _opa.query(
        "civitas/superset",
        "chart_dataset_access",
        {"user": sub, "civitasDatasetIds": candidate_civitas_ids},
    )
    allowed_dataset_civitas_ids = set((result or {}).get("allowedDatasetIds", []))
    ids = [
        chart_id
        for chart_id, ds_id in rows
        if dataset_civitas_ids.get(ds_id) in allowed_dataset_civitas_ids
    ]
    logger.warning("resolve_extra_chart_ids: sub=%s → ids=%s", sub, ids)
    return db.session.query(Slice.id).filter(Slice.id.in_(ids)) if ids else empty


def resolve_extra_editor_subject_ids(resource) -> list[int]:
    """EXTRA_EDITORS_RESOLVER: makes Superset's own UI-side editorship affordances agree with the
    OPA decision is_editor()/raise_for_editorship() already enforce server-side.

    The Explore page's "Overwrite" option is computed client-side (canOverwriteSlice.ts) from
    resource.editors UNION resource.extra_editors — never from is_editor() itself. Since nothing
    here writes into Superset's own native `editors` Subject relationship (access is meant to be
    determined entirely externally, not mirrored into Superset's DB), a user with a real
    OPA-granted write permission passed every actual write check but never saw the option to use
    it. This resolver feeds `extra_editors` instead: Superset's own get_extra_editor_subject_ids()
    (server-side) and the chart/dashboard schemas' `extra_editors` field (frontend) both call it,
    so returning this user's own Subject id here — only when OPA actually grants it — makes both
    sides agree without duplicating the grant into Superset's own tables.
    """
    sm = current_app.appbuilder.sm
    sub = sm._sub()
    if not sub:
        return []

    from superset.connectors.sqla.models import SqlaTable
    from superset.models.dashboard import Dashboard
    from superset.models.slice import Slice

    if isinstance(resource, SqlaTable):
        allowed = sm._opa_owns_dataset(sub, resource)
    elif isinstance(resource, Slice):
        allowed = sm._opa_owns_chart(sub, resource)
    elif isinstance(resource, Dashboard):
        allowed = sm._opa_owns_dashboard(sub, resource)
    else:
        allowed = False

    if not allowed:
        return []

    user = sm.find_user(username=sub)
    if user is None:
        return []

    from superset.subjects.utils import get_or_create_user_subject

    subject = get_or_create_user_subject(user.id)
    logger.warning("resolve_extra_editor_subject_ids: sub=%s resource=%s → subject=%s", sub, resource, subject)
    return [subject.id] if subject else []


class CivitasSupersetSecurityManager(SupersetSecurityManager):

    # Superset's own Gamma/Alpha role sync (sync_role_definitions(), run by `superset init`)
    # classifies a view as Alpha-only whenever it exposes a permission outside this set
    # (see _is_alpha_only() in superset.security.manager). DatasourceRestApi.combined_list
    # has no method_permission_name override, so FAB's @protect() falls back to the method
    # name itself ("can_combined_list") as the required permission — which isn't in the
    # upstream default set, so Gamma users get a 403 on GET /api/v1/datasource/ before
    # civitas_security.py or OPA are ever consulted. Add it so Gamma keeps read access.
    READ_ONLY_PERMISSION = SupersetSecurityManager.READ_ONLY_PERMISSION | {"can_combined_list"}

    def create_login_manager(self, app):
        """Extend the default LoginManager with a request_loader that resolves Bearer JWT
        tokens to real User objects.

        FAB's @protect() validates JWTs for authorization but does not call login_user(),
        so flask_login.current_user stays AnonymousUserMixin for JWT-only requests. Many
        parts of Superset (e.g. CreateDashboardCommand) read current_user to set the
        resource owner; without this loader they receive AnonymousUserMixin and crash with
        an SQLAlchemy AttributeError.
        """
        lm = super().create_login_manager(app)

        @lm.request_loader
        def load_user_from_bearer(req):
            auth = req.headers.get("Authorization", "")
            if not auth.startswith("Bearer "):
                return None
            try:
                from flask_jwt_extended import decode_token
                decoded = decode_token(auth[7:].strip(), allow_expired=True)
                identity = decoded.get("sub") or decoded.get("identity")
                if identity:
                    try:
                        user = self.get_user_by_id(int(identity))
                        if user and not getattr(user, "is_anonymous", True):
                            return user
                    except (ValueError, TypeError):
                        pass
            except Exception as e:
                logger.debug("Bearer token user loader failed: %s", e)
            return None

        return lm

    def get_oauth_user_info(self, provider: str, resp: dict[str, Any]) -> dict[str, Any]:
        if provider != "keycloak":
            return super().get_oauth_user_info(provider, resp)

        me = self.oauth.keycloak.userinfo()
        required_group = os.getenv("KEYCLOAK_SUPERSET_GROUP", "superset-users")
        groups = me.get("groups", []) or []

        if required_group not in groups:
            return {}

        logger.warning(me)

        return {
            # setting the externalUserId as username, needed for Trino impersonation
            "username": me.get("sub", ""),
            "first_name": me.get("given_name", ""),
            "last_name": me.get("family_name", ""),
            "email": me.get("email", ""),
            "role_keys": groups,
        }

    def _opa_can_access(self, rule: str, sub: str, input_data: dict) -> bool:
        try:
            return _opa.query("civitas/superset", rule, {"user": sub, **input_data}) is True
        except Exception as e:
            logger.error("OPA query failed rule=%s sub=%s: %s", rule, sub, e)
            return False

    def _opa_check_open_data(self, rule: str, input_data: dict) -> bool:
        """Anonymous-safe variant of _opa_can_access: omits "user" entirely rather than
        sending sub=None/"" — the open-data branches these rules call are identity-agnostic,
        and an explicit null/empty user value risks tripping user_context_fetcher's own
        string-concat logic instead of just leaving input.user undefined as intended.
        """
        try:
            return _opa.query("civitas/superset", rule, input_data) is True
        except Exception as e:
            logger.error("OPA open-data query failed rule=%s: %s", rule, e)
            return False

    def _sub(self) -> str | None:
        # Session / OAuth users: Flask-Login populates g.user from the session cookie.
        user = getattr(g, "user", None)
        logger.warning("_sub: g.user=%r is_anonymous=%r username=%r",
                       user, getattr(user, "is_anonymous", "N/A"),
                       getattr(user, "username", "N/A"))
        if user and not getattr(user, "is_anonymous", True):
            username = getattr(user, "username", None)
            if username:
                logger.warning("_sub: returning username=%r from g.user", username)
                return username
        # Service accounts using JWT Bearer: security manager methods may be called from
        # before_request hooks before @protect() has run. Decode the token directly so we
        # can identify the caller even when the token has already expired — @protect() is
        # the real authentication gate; we only need the identity for the OPA bypass.
        try:
            from flask import request as flask_request
            from flask_jwt_extended import decode_token
            auth = flask_request.headers.get("Authorization", "")
            if auth.startswith("Bearer "):
                decoded = decode_token(auth[7:].strip(), allow_expired=True)
                identity = decoded.get("sub") or decoded.get("identity")
                logger.warning("_sub: jwt identity=%r (allow_expired)", identity)
                if identity:
                    # Superset stores the numeric user ID as sub; resolve to username.
                    try:
                        user = self.get_user_by_id(int(identity))
                        if user and not getattr(user, "is_anonymous", True):
                            username = getattr(user, "username", None)
                            if username:
                                logger.warning("_sub: resolved jwt id=%r → username=%r", identity, username)
                                return username
                    except (ValueError, TypeError):
                        pass
                    return str(identity)
        except Exception as e:
            logger.warning("_sub: decode_token failed: %s", e)
        logger.warning("_sub: returning None")
        return None

    def _is_local_admin(self) -> bool:
        sub = self._sub()
        result = bool(_LOCAL_ADMIN_USER) and sub == _LOCAL_ADMIN_USER
        logger.warning("_is_local_admin: sub=%r LOCAL_ADMIN_USER=%r result=%s XXX", sub, _LOCAL_ADMIN_USER, result)
        return result

    def _civitas_dataset_id(self, datasource) -> str | None:
        return _civitas_dataset_id(datasource)

    def can_access_schema(self, datasource) -> bool:
        if self._is_local_admin():
            return super().can_access_schema(datasource)
        civitas_id = self._civitas_dataset_id(datasource)
        sub = self._sub()
        if not sub:
            result = bool(civitas_id) and self._opa_check_open_data(
                "can_access_schema", {"civitasDatasetId": civitas_id}
            )
            logger.warning("can_access_schema: anonymous civitas_id=%s → %s", civitas_id, result)
            return result
        if civitas_id is None:
            return super().can_access_schema(datasource)
        result = self._opa_can_access("can_access_schema", sub, {"civitasDatasetId": civitas_id})
        logger.warning("can_access_schema: sub=%s civitas_id=%s → %s", sub, civitas_id, result)
        return result

    def can_access_datasource(self, datasource) -> bool:
        if self._is_local_admin():
            return super().can_access_datasource(datasource)
        civitas_id = self._civitas_dataset_id(datasource)
        sub = self._sub()
        if not sub:
            result = bool(civitas_id) and self._opa_check_open_data(
                "can_access_datasource", {"civitasDatasetId": civitas_id}
            )
            logger.warning("can_access_datasource: anonymous civitas_id=%s → %s", civitas_id, result)
            return result
        result = self._opa_can_access("can_access_datasource", sub, {"civitasDatasetId": civitas_id})
        logger.warning("can_access_datasource: sub=%s civitas_id=%s → %s", sub, civitas_id, result)
        return result

    def _dashboard_dataset_ids(self, dashboard) -> set[str]:
        """The set of civitasDatasetIds backing a dashboard's charts — the portal DB has no
        visibility into chart/dataset composition, so this is derived here at request time by
        walking dashboard.slices -> slice.datasource -> datasource.extra.civitasDatasetId."""
        ids: set[str] = set()
        for slice_ in getattr(dashboard, "slices", None) or []:
            civitas_id = self._civitas_dataset_id(getattr(slice_, "datasource", None))
            if civitas_id:
                ids.add(civitas_id)
        return ids

    def _opa_dashboard_is_open_data(self, dashboard) -> bool:
        dataset_ids = list(self._dashboard_dataset_ids(dashboard))
        if not dataset_ids:
            return False
        return self._opa_check_open_data(
            "can_access_dashboard", {"dashboard_dataset_ids": dataset_ids}
        )

    def raise_for_access(self, **kwargs) -> None:
        """Dashboard-container visibility is fully OPA-gated here, both anonymous and
        authenticated — Superset's own native RBAC (DASHBOARD_RBAC/dashboard.roles) is never
        consulted for a dashboard resource. Chart data on an opened dashboard is independently
        re-gated per-datasource by can_access_datasource above — passing this check alone does
        not unlock chart data by itself.
        """
        dashboard = kwargs.get("dashboard")
        if dashboard is not None and not self._is_local_admin():
            dashboard_id = getattr(dashboard, "id", None)
            sub = self._sub()
            if not sub:
                if getattr(dashboard, "published", False) and self._opa_dashboard_is_open_data(dashboard):
                    logger.warning(
                        "raise_for_access: anonymous dashboard_id=%s → allowed (open data)", dashboard_id
                    )
                    return
                logger.warning("raise_for_access: anonymous dashboard_id=%s → denied", dashboard_id)
            else:
                # civitasDatasetId resolution happens inside can_read_dashboard itself (via
                # dashboard_dataset_fetcher, keyed on externalDashboardId) — OPA is the only
                # caller of authz/repository's dashboard-dataset endpoint; Superset never calls
                # it directly.
                if dashboard_id is not None and self._opa_can_access(
                    "can_read_dashboard",
                    sub,
                    {
                        "externalDashboardId": str(dashboard_id),
                        "published": bool(getattr(dashboard, "published", False)),
                    },
                ):
                    logger.warning(
                        "raise_for_access: sub=%s dashboard_id=%s → allowed", sub, dashboard_id
                    )
                    return
                # Open-data bypass, parity with the anonymous branch above: a logged-in user
                # with no DASHBOARD grant can still open a fully public dashboard, rather than
                # being denied purely for being authenticated. Reuses can_access_dashboard's
                # "every backing dataset is open" check (dashboard_dataset_ids) rather than a
                # single home dataset — that's the same guarantee the anonymous path relies on.
                if getattr(dashboard, "published", False) and self._opa_dashboard_is_open_data(dashboard):
                    logger.warning(
                        "raise_for_access: sub=%s dashboard_id=%s → allowed (open data)",
                        sub, dashboard_id,
                    )
                    return
                logger.warning("raise_for_access: sub=%s dashboard_id=%s → denied", sub, dashboard_id)
            raise SupersetSecurityException(
                SupersetError(
                    error_type=SupersetErrorType.MISSING_OWNERSHIP_ERROR,
                    message=_(
                        "You don't have the rights to access %(resource)s",
                        resource=dashboard,
                    ),
                    level=ErrorLevel.ERROR,
                )
            )
        super().raise_for_access(**kwargs)

    def can_write_dashboard(self, dashboard) -> bool:
        if self._is_local_admin():
            return True
        sub = getattr(getattr(g, "user", None), "username", None)
        dashboard_id = getattr(dashboard, "id", None)
        if not sub:
            logger.warning("can_write_dashboard: no sub → False")
            return False
        result = self._opa_owns_dashboard(sub, dashboard)
        logger.warning("can_write_dashboard: sub=%s dashboard_id=%s → %s", sub, dashboard_id, result)
        return result

    def _opa_owns_dashboard(self, sub: str, dashboard) -> bool:
        # civitasDatasetId resolution happens inside can_write_dashboard itself (via
        # dashboard_dataset_fetcher, keyed on externalDashboardId) — see raise_for_access's
        # matching comment for why Superset never calls authz/repository directly.
        dashboard_id = getattr(dashboard, "id", None)
        if dashboard_id is None:
            logger.warning("_opa_owns_dashboard: dashboard has no id → False")
            return False
        result = self._opa_can_access(
            "can_write_dashboard", sub, {"externalDashboardId": str(dashboard_id)}
        )
        logger.warning(
            "_opa_owns_dashboard: sub=%s dashboard_id=%s → %s", sub, dashboard_id, result
        )
        return result

    def _opa_owns_chart(self, sub: str, chart) -> bool:
        datasource = getattr(chart, "datasource", None)
        civitas_id = self._civitas_dataset_id(datasource) if datasource else None
        if not civitas_id:
            return False
        result = self._opa_can_access("can_write_chart", sub, {"civitasDatasetId": civitas_id})
        logger.warning("_opa_owns_chart: sub=%s chart_id=%s civitas_id=%s → %s",
                       sub, getattr(chart, "id", None), civitas_id, result)
        return result

    def _opa_owns_dataset(self, sub: str, dataset) -> bool:
        civitas_id = self._civitas_dataset_id(dataset)
        if not civitas_id:
            return False
        result = self._opa_can_access("can_write_dataset", sub, {"civitasDatasetId": civitas_id})
        logger.warning("_opa_owns_dataset: sub=%s dataset_id=%s civitas_id=%s → %s",
                       sub, getattr(dataset, "id", None), civitas_id, result)
        return result

    def is_editor(self, resource) -> bool:
        """Civitas-managed resource types (dataset/chart/dashboard) are decided by OPA alone —
        deliberately checked BEFORE any native fallback, not after: falling back to
        super().is_editor() first would let a resource's native Superset editors/owners list
        (e.g. auto-populated for whoever created a chart) silently grant editorship independent
        of whatever the external system currently says, which defeats the "read/write fully
        determined externally" requirement for exactly the resource types Civitas governs. The
        native fallback still applies for any other resource type this subclass doesn't manage.
        """
        if self._is_local_admin():
            return True
        sub = getattr(getattr(g, "user", None), "username", None)
        resource_id = getattr(resource, "id", None)
        from superset.connectors.sqla.models import SqlaTable
        from superset.models.dashboard import Dashboard
        from superset.models.slice import Slice
        if isinstance(resource, SqlaTable) and sub:
            result = self._opa_owns_dataset(sub, resource)
            logger.warning("is_editor: sub=%s dataset_id=%s → %s (OPA)", sub, resource_id, result)
            return result
        if isinstance(resource, Slice) and sub:
            result = self._opa_owns_chart(sub, resource)
            logger.warning("is_editor: sub=%s chart_id=%s → %s (OPA)", sub, resource_id, result)
            return result
        if isinstance(resource, Dashboard) and sub:
            result = self._opa_owns_dashboard(sub, resource)
            logger.warning("is_editor: sub=%s dashboard_id=%s → %s (OPA)", sub, resource_id, result)
            return result
        if super().is_editor(resource):
            logger.warning(
                "is_editor: sub=%s resource_id=%s → True (native editors list)", sub, resource_id
            )
            return True
        logger.warning("is_editor: sub=%s resource_id=%s → False", sub, resource_id)
        return False

    def raise_for_editorship(self, resource) -> None:
        if self._is_local_admin():
            return
        from superset.connectors.sqla.models import SqlaTable
        from superset.models.dashboard import Dashboard
        from superset.models.slice import Slice
        sub = getattr(getattr(g, "user", None), "username", None)
        resource_id = getattr(resource, "id", None)
        logger.warning(
            "raise_for_editorship: entered resource_type=%s resource_id=%s sub=%s "
            "is_dashboard=%s is_slice=%s is_sqla_table=%s",
            type(resource).__name__, resource_id, sub,
            isinstance(resource, Dashboard), isinstance(resource, Slice), isinstance(resource, SqlaTable),
        )
        if isinstance(resource, SqlaTable) and sub:
            civitas_id = self._civitas_dataset_id(resource)
            if civitas_id:
                if self._opa_can_access("can_write_dataset", sub, {"civitasDatasetId": civitas_id}):
                    logger.warning("raise_for_editorship: dataset_id=%s sub=%s civitas_id=%s → allowed",
                                   resource_id, sub, civitas_id)
                    return
                logger.warning("raise_for_editorship: dataset_id=%s sub=%s civitas_id=%s → denied",
                               resource_id, sub, civitas_id)
                raise SupersetSecurityException(
                    SupersetError(
                        error_type=SupersetErrorType.MISSING_OWNERSHIP_ERROR,
                        message=_(
                            "You don't have the rights to alter %(resource)s",
                            resource=resource,
                        ),
                        level=ErrorLevel.ERROR,
                    )
                )
        if isinstance(resource, Slice) and sub:
            datasource = getattr(resource, "datasource", None)
            civitas_id = self._civitas_dataset_id(datasource) if datasource else None
            if civitas_id:
                allowed = self._opa_can_access("can_write_chart", sub, {"civitasDatasetId": civitas_id})
                logger.warning("raise_for_editorship: chart_id=%s sub=%s civitas_id=%s → %s",
                               resource_id, sub, civitas_id, 'allowed' if allowed else 'denied')
                if allowed:
                    return
                raise SupersetSecurityException(
                    SupersetError(
                        error_type=SupersetErrorType.MISSING_OWNERSHIP_ERROR,
                        message=_(
                            "You don't have the rights to alter %(resource)s",
                            resource=resource,
                        ),
                        level=ErrorLevel.ERROR,
                    )
                )
        if isinstance(resource, Dashboard) and sub:
            allowed = self._opa_owns_dashboard(sub, resource)
            logger.warning("raise_for_editorship: dashboard_id=%s sub=%s → %s", resource_id, sub, 'allowed' if allowed else 'denied')
            if allowed:
                return
            raise SupersetSecurityException(
                SupersetError(
                    error_type=SupersetErrorType.MISSING_OWNERSHIP_ERROR,
                    message=_(
                        "You don't have the rights to alter %(resource)s",
                        resource=resource,
                    ),
                    level=ErrorLevel.ERROR,
                )
            )
        super().raise_for_editorship(resource)

    def user_view_menu_names(self, permission_name: str) -> set[str]:
        if self._is_local_admin():
            return super().user_view_menu_names(permission_name)
        user = getattr(g, "user", None)
        sub = getattr(user, "username", None) if user else None
        logger.warning("user_view_menu_names: permission_name=%s sub=%s", permission_name, sub)

        if permission_name == "schema_access" and sub:
            return self._schema_perms_from_opa(sub)

        ret_val = super().user_view_menu_names(permission_name)
        logger.warning(ret_val)
        return ret_val

    def _schema_perms_from_opa(self, sub: str) -> set[str]:
        result = _opa.query("civitas/superset", "schema_access", {"user": sub})
        if result is None:
            return set()

        if result.get("is_tenant_reader"):
            logger.warning("_schema_perms_from_opa: sub=%s is_tenant_reader → all civitas schemas", sub)
            return self._all_civitas_schema_perms()

        dataset_ids: set[str] = set(result.get("allowed_schemas", []))
        logger.warning("_schema_perms_from_opa: sub=%s dataset_ids=%s", sub, dataset_ids)
        perms = {
            f"[{_TRINO_DB_NAME}].[civitas].[{_workspace_name(dataset_id)}]"
            for dataset_id in dataset_ids
        }
        logger.warning("_schema_perms_from_opa: sub=%s perms=%s", sub, perms)
        return perms

    def _all_civitas_schema_perms(self) -> set[str]:
        """Return all registered schema_access permission strings for the Trino database.

        Used when the user has tenant-wide DATASET_PAYLOAD_READ — they should see every
        dataset schema that has been registered in Superset, not just the ones explicitly
        assigned to them.

        Superset stores schema_access permissions as ViewMenu entries with the format
        "[DatabaseName].[schema]" (ending with "]"). Datasource-level permissions have the
        format "[DatabaseName].[schema].[table] (DatasetType)" and are excluded by the
        trailing "]" in the LIKE pattern.
        """
        try:
            from flask_appbuilder.security.sqla.models import ViewMenu
            from superset import db
            rows = (
                db.session.query(ViewMenu.name)
                .filter(ViewMenu.name.like(f"[{_TRINO_DB_NAME}].[%]"))
                .all()
            )
            perms = {row.name for row in rows}
            logger.warning("_all_civitas_schema_perms: found %d perms", len(perms))
            return perms
        except Exception as e:
            logger.warning("_all_civitas_schema_perms: failed: %s", e)
            return set()
