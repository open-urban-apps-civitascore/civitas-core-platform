import json
import logging
import os
from typing import Any
from uuid import UUID

from flask import g
from superset.security.manager import SupersetSecurityManager

from opa_client import OpaClient

logger = logging.getLogger(__name__)
_opa = OpaClient(os.getenv("OPA_URL", "http://civitas-opa:8181"))


class CivitasSupersetSecurityManager(SupersetSecurityManager):
    """Keep Keycloak identity and CORE data checks; use Superset Subjects for assets.

    Dataset, chart, and dashboard editors/viewers are managed by Superset's native
    Subject relationships. CORE OPA remains a data-plane check for Civitas datasets,
    and Trino independently enforces the same CORE payload permission on every query.
    """

    READ_ONLY_PERMISSION = SupersetSecurityManager.READ_ONLY_PERMISSION | {"can_combined_list"}
    CORE_GROUP_DESCRIPTION_PREFIX = "Managed by CIVITAS CORE; group id="

    def create_login_manager(self, app):
        """Load Superset users for API requests authenticated with a Bearer token."""
        login_manager = super().create_login_manager(app)

        @app.before_request
        def install_public_dashboard_filter():
            # Dashboard models cannot be imported while Superset is loading its config.
            # Install after app initialization, before any dashboard request is handled.
            if getattr(self, "_public_dashboard_filter_installed", False):
                return
            from sqlalchemy import and_, or_
            from superset.daos.dashboard import DashboardDAO
            from superset.dashboards.filters import DashboardAccessFilter
            from superset.models.dashboard import Dashboard
            from superset.subjects.models import Subject
            from superset.utils.filters import guest_embedded_dashboard_filter

            class CivitasDashboardAccessFilter(DashboardAccessFilter):
                """Pass published Public dashboards to CORE's access check."""

                def apply(self, query, value):
                    if guest_embedded_dashboard_filter() is not None:
                        return super().apply(query, value)
                    native_ids = super().apply(query, value).with_entities(Dashboard.id)
                    public_viewer = Dashboard.viewers.any(
                        and_(Subject.type == 2, Subject.label == "Public")
                    )
                    return query.filter(
                        or_(
                            Dashboard.id.in_(native_ids),
                            and_(Dashboard.published.is_(True), public_viewer),
                        )
                    )

            DashboardDAO.base_filter = CivitasDashboardAccessFilter
            self._public_dashboard_filter_installed = True

        @login_manager.request_loader
        def load_user_from_bearer(request):
            authorization = request.headers.get("Authorization", "")
            if not authorization.startswith("Bearer "):
                return None

            try:
                from flask_jwt_extended import decode_token

                decoded = decode_token(authorization[7:].strip())
                if decoded.get("type") != "access":
                    return None
                identity = decoded.get("sub") or decoded.get("identity")
                if identity:
                    try:
                        user = self.get_user_by_id(int(identity))
                        if user and not getattr(user, "is_anonymous", True):
                            return user
                    except (TypeError, ValueError):
                        pass
            except Exception as exc:  # noqa: BLE001
                logger.debug("Bearer token user lookup failed: %s", exc)
            return None

        return login_manager

    def get_oauth_user_info(self, provider: str, resp: dict[str, Any]) -> dict[str, Any]:
        if provider != "keycloak":
            return super().get_oauth_user_info(provider, resp)

        user_info = self.oauth.keycloak.userinfo()
        groups = user_info.get("groups", []) or []
        subject = user_info.get("sub", "")
        try:
            dashboard_access = _opa.query(
                "civitas/superset", "dashboard_users", {"user": subject}
            ) is True
        except Exception as exc:  # noqa: BLE001
            logger.warning("CORE dashboard access lookup failed: %s", exc)
            dashboard_access = False
        if not dashboard_access and "superset-admins" not in groups:
            return {}

        return {
            # Trino impersonation uses the Keycloak subject as X-Trino-User.
            "username": subject,
            "first_name": user_info.get("given_name", ""),
            "last_name": user_info.get("family_name", ""),
            "email": user_info.get("email", ""),
            "role_keys": [*groups, "dashboard-users"] if dashboard_access else groups,
        }

    def auth_user_oauth(self, userinfo: dict[str, Any]):
        """Authenticate through FAB, then reconcile CORE groups into FAB groups."""
        user = super().auth_user_oauth(userinfo)
        if not user:
            return None

        subject = userinfo.get("username")
        result = _opa.query("civitas/superset", "user_groups", {"user": subject})
        if not isinstance(result, dict) or not isinstance(result.get("groups"), list):
            logger.error("CORE group lookup failed during Superset OAuth login")
            return None

        try:
            self._sync_core_groups(user, result["groups"])
        except Exception as exc:  # noqa: BLE001
            self.session.rollback()
            logger.exception(
                "CORE group reconciliation failed during Superset OAuth login: %s", exc
            )
            return None
        return user

    def _sync_core_groups(self, user, core_groups: list[dict[str, Any]]) -> None:
        """Mirror CORE group identities and this user's memberships into native FAB groups."""
        from superset.subjects.sync import sync_group_subject

        desired_groups = {}
        for core_group in core_groups:
            try:
                core_group_id = str(UUID(str(core_group["id"])))
                core_group_name = core_group["name"]
                if not isinstance(core_group_name, str) or not core_group_name.strip():
                    raise ValueError("CORE group name is missing")
            except (KeyError, TypeError, ValueError) as exc:
                raise ValueError("CORE group identity is incomplete") from exc

            fab_group_name = f"core-{core_group_id}"
            description = f"{self.CORE_GROUP_DESCRIPTION_PREFIX}{core_group_id}"
            fab_group = (
                self.session.query(self.group_model)
                .filter_by(name=fab_group_name)
                .one_or_none()
            )
            if fab_group is None:
                from superset.daos.group import GroupDAO

                fab_group = GroupDAO.create(
                    attributes={
                        "name": fab_group_name,
                        "label": core_group_name[:150],
                        "description": description,
                        "roles": [],
                    }
                )
                self.session.flush()
            else:
                if not (fab_group.description or "").startswith(
                    self.CORE_GROUP_DESCRIPTION_PREFIX
                ):
                    raise ValueError("CORE group name conflicts with an unmanaged FAB group")
                fab_group.label = core_group_name[:150]
                fab_group.description = description
            # CORE groups carry no roles by default. The adapter can attach one
            # narrow role to groups that edit a CORE-provisioned dashboard; keep
            # that managed grant when OAuth reconciles group membership.
            creator_role = self.find_role("CORE Dashboard Dataset Creator")
            fab_group.roles = (
                [role for role in (fab_group.roles or []) if role.id == creator_role.id]
                if creator_role
                else []
            )

            sync_group_subject(fab_group)
            desired_groups[core_group_id] = fab_group

        managed_groups = {
            group.id
            for group in self.session.query(self.group_model)
            .filter(self.group_model.description.like(f"{self.CORE_GROUP_DESCRIPTION_PREFIX}%"))
            .all()
        }
        retained_groups = [
            group for group in (user.groups or []) if group.id not in managed_groups
        ]
        user.groups = retained_groups + list(desired_groups.values())
        self.session.commit()

    def _keycloak_subject(self) -> str | None:
        user = getattr(g, "user", None)
        username = getattr(user, "username", None) if user else None
        return username or None

    def _opa_dataset_access(self, rule: str, datasource) -> bool:
        """Check CORE's dataset payload permission for Civitas-owned data sources."""
        try:
            extra = datasource.extra or "{}"
            civitas_dataset_id = json.loads(extra).get("civitasDatasetId")
        except (AttributeError, TypeError, ValueError):
            civitas_dataset_id = None

        if not civitas_dataset_id:
            return False

        subject = self._keycloak_subject()
        if not subject:
            # OPA's anonymous branch only grants datasets explicitly published as open data.
            try:
                return _opa.query(
                    "civitas/superset",
                    rule,
                    {"civitasDatasetId": civitas_dataset_id},
                ) is True
            except Exception as exc:  # noqa: BLE001
                logger.warning("Anonymous CORE dataset check failed: %s", exc)
                return False

        try:
            return _opa.query(
                "civitas/superset",
                rule,
                {"user": subject, "civitasDatasetId": civitas_dataset_id},
            ) is True
        except Exception as exc:  # noqa: BLE001
            logger.warning("CORE dataset check failed for %s: %s", rule, exc)
            return False

    def can_access_schema(self, datasource) -> bool:
        # Superset-native schema grants continue to work. OPA adds CORE dataset
        # access and the existing anonymous open-data path.
        return super().can_access_schema(datasource) or self._opa_dataset_access(
            "can_access_schema", datasource
        )

    def can_access_datasource(self, datasource) -> bool:
        # A Superset grant may expose the resource UI, while Trino still enforces
        # CORE's DATASET_PAYLOAD_READ permission before returning query rows.
        return super().can_access_datasource(datasource) or self._opa_dataset_access(
            "can_access_datasource", datasource
        )

    def is_viewer(self, resource) -> bool:
        """Check live CORE rights for dashboards; accept anonymous public dashboards."""
        from superset.models.dashboard import Dashboard

        if not isinstance(resource, Dashboard):
            return super().is_viewer(resource)
        if self.is_admin():
            return True
        subject = self._keycloak_subject()
        if subject and self._core_dashboard_access(
            "can_read_core_dashboard", resource, subject
        ):
            return True
        if not getattr(resource, "published", False):
            return False
        if not hasattr(resource, "viewers"):
            return False
        public_role_viewer = any(
            subject.type == 2 and subject.label == "Public"
            for subject in resource.viewers
        )
        if not public_role_viewer:
            return False
        try:
            return _opa.query(
                "civitas/superset",
                "can_view_public_dashboard",
                {"externalDashboardId": str(resource.id)},
            ) is True
        except Exception as exc:  # noqa: BLE001
            logger.warning("Public CORE dashboard check failed: %s", exc)
            return False

    def is_editor(self, resource) -> bool:
        from superset.models.dashboard import Dashboard

        if not isinstance(resource, Dashboard):
            return super().is_editor(resource)
        if self.is_guest_user():
            return False
        if self.is_admin():
            return True
        subject = self._keycloak_subject()
        return bool(subject) and self._core_dashboard_access(
            "can_edit_core_dashboard", resource, subject
        )

    def _core_dashboard_access(self, rule, dashboard, subject) -> bool:
        try:
            return _opa.query(
                "civitas/superset",
                rule,
                {
                    "user": subject,
                    "externalDashboardId": str(dashboard.id),
                    "published": bool(dashboard.published),
                },
            ) is True
        except Exception as exc:  # noqa: BLE001
            logger.warning("CORE dashboard check failed for %s: %s", rule, exc)
            return False

    def user_view_menu_names(self, permission_name: str) -> set[str]:
        native_permissions = super().user_view_menu_names(permission_name)
        if permission_name != "schema_access":
            return native_permissions

        subject = self._keycloak_subject()
        if not subject:
            return native_permissions

        try:
            schema_names = (
                _opa.query(
                    "civitas/superset", "allowed_geodata_schemas", {"user": subject}
                )
                or []
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning("CORE schema permissions lookup failed: %s", exc)
            return native_permissions

        database_name = os.getenv("SUPERSET_TRINO_DATABASE_NAME", "CORE")
        core_schema_permissions = {
            f"[{database_name}].[geodata].[{schema_name}]"
            for schema_name in schema_names
            if isinstance(schema_name, str) and schema_name
        }
        return native_permissions | core_schema_permissions
