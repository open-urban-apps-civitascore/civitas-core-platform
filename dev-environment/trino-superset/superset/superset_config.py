import os

from flask_appbuilder.security.manager import AUTH_OAUTH
from superset.config import THEME_DARK, THEME_DEFAULT
from superset.subjects.types import SubjectType

from civitas_security import (
    CivitasSupersetSecurityManager,
)


def env(name: str, default: str | None = None) -> str:
    value = os.getenv(name, default)
    if value is None:
        raise RuntimeError(f"Missing environment variable: {name}")
    return value


SECRET_KEY = env("SUPERSET_SECRET_KEY")
SQLALCHEMY_DATABASE_URI = (
    f"postgresql+psycopg2://{env('SUPERSET_DB_USER')}:{env('SUPERSET_DB_PASSWORD')}"
    f"@{env('SUPERSET_DB_HOST')}:{env('SUPERSET_DB_PORT')}/{env('SUPERSET_DB_NAME')}"
)

TALISMAN_ENABLED = False
WTF_CSRF_ENABLED = True
ENABLE_PROXY_FIX = True
AUTH_TYPE = AUTH_OAUTH
# Flask-AppBuilder's POST /api/v1/security/login rejects any provider other than the one
# AUTH_TYPE maps to (db/ldap) once api_login_allow_multiple_providers is off — and AUTH_OAUTH
# maps to no provider at all, so that check now also rejects provider="db" outright (closed by
# FAB's validate_provider fix, landing in flask-appbuilder 5.2.2). SupersetSession.java's service
# account (civitas-config-adapter) logs in with provider="db" precisely because it can't do an
# interactive Keycloak redirect — this flag is FAB's own documented opt-in to allow that
# alongside real users' OAuth logins, not a workaround for something else.
AUTH_API_LOGIN_ALLOW_MULTIPLE_PROVIDERS = True
# Superset must create its local FAB user on the first successful Keycloak login.
# CivitasSupersetSecurityManager only returns OAuth user info when CORE grants
# dashboard access (or the user is a designated Superset administrator).
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = "Public"
PUBLIC_ROLE_LIKE = "Public"
# Lets genuinely unauthenticated requests through Flask-AppBuilder's @protect() as the "Public"
# role (distinct from PUBLIC_ROLE_LIKE above, which only seeds that role's permission set). OPA
# still gates open-data and dataset payload access; Superset Subjects govern dashboard/chart
# visibility and editorship.
AUTH_ROLE_PUBLIC = "Public"
AUTH_ROLES_SYNC_AT_LOGIN = True
AUTH_ROLES_MAPPING = {
    "superset-admins": ["Admin"],
    "dashboard-users": ["Gamma"],
}
CUSTOM_SECURITY_MANAGER = CivitasSupersetSecurityManager
OAUTH_PROVIDERS = [
    {
        "name": "keycloak",
        "icon": "fa-key",
        "token_key": "access_token",
        "remote_app": {
            "client_id": env("KEYCLOAK_SUPERSET_CLIENT_ID"),
            "client_secret": env("KEYCLOAK_SUPERSET_CLIENT_SECRET"),
            "api_base_url": f"{env('KEYCLOAK_INTERNAL_URL')}/realms/{env('KEYCLOAK_REALM')}/protocol/openid-connect/",
            "access_token_url": f"{env('KEYCLOAK_INTERNAL_URL')}/realms/{env('KEYCLOAK_REALM')}/protocol/openid-connect/token",
            "authorize_url": f"{env('KEYCLOAK_PUBLIC_URL')}/realms/{env('KEYCLOAK_REALM')}/protocol/openid-connect/auth",
            "userinfo_endpoint": f"{env('KEYCLOAK_INTERNAL_URL')}/realms/{env('KEYCLOAK_REALM')}/protocol/openid-connect/userinfo",
            "jwks_uri": f"{env('KEYCLOAK_INTERNAL_URL')}/realms/{env('KEYCLOAK_REALM')}/protocol/openid-connect/certs",
            "request_token_url": None,
            "client_kwargs": {"scope": "openid email profile"},
        },
    }
]
FEATURE_FLAGS = {
    "ENABLE_TEMPLATE_PROCESSING": True,
    # Per-resource dashboard/chart visibility is managed by native Superset Subject assignments.
    # Dataset query access remains separately gated by CORE OPA and Trino.
    "ENABLE_VIEWERS": True,
    # Keep datasource access checks in place for viewer-assigned charts/dashboards.
    "VIEWER_PROMISCUOUS_MODE": False,
    "ASSIGN_CREATOR_GROUPS_AS_VIEWERS": False,
}
# CORE is authoritative for resource membership. CORE-synchronized FAB Groups are available as
# Subjects; dashboards also permit the Public role Subject for anonymous access. CORE group IDs,
# names, and memberships are synchronized at OAuth login. Groups receive no FAB roles by default;
# the adapter grants the narrow CORE Dashboard Dataset Creator role to dashboard editor groups.
SUBJECTS_RELATED_TYPES = [SubjectType.GROUP]
SUBJECTS_RELATED_TYPES_DASHBOARDS = [SubjectType.GROUP, SubjectType.ROLE]


def core_groups_only(query, *args, **kwargs):
    from flask_appbuilder.security.sqla.models import Group

    return query.filter(Group.description.like("Managed by CIVITAS CORE; group id=%"))


EXTRA_RELATED_QUERY_FILTERS = {"group": core_groups_only}

# Superset ships with i18n off (LANGUAGES = {}) since most locales are incomplete; German is
# maintained well enough to use. Keeping "en" too so the flag-icon switcher can still fall back.
LANGUAGES = {
    "en": {"flag": "us", "name": "English"},
    "de": {"flag": "de", "name": "German"},
}
BABEL_DEFAULT_LOCALE = "de"

LOGOUT_REDIRECT_URL = (
    f"{env('KEYCLOAK_PUBLIC_URL')}/realms/{env('KEYCLOAK_REALM')}"
    f"/protocol/openid-connect/logout"
    f"?post_logout_redirect_uri={env('SUPERSET_PUBLIC_URL')}/login"
    f"&client_id={env('KEYCLOAK_SUPERSET_CLIENT_ID')}"
)

ROW_LIMIT = 5000
SQLLAB_CTAS_NO_LIMIT = True

# Civitas branding. APP_NAME/APP_ICON alone wouldn't take effect here since THEME_DEFAULT
# already captured their old values at import time (see the note on APP_NAME in
# superset/config.py) — mutate the theme tokens directly instead, matching portal-frontend's
# brand mark (public/images/only_logo_civitas.svg) and primary color (--primary in globals.css,
# oklch(49.999% 0.11934 242.772) == #0369a1).
APP_NAME = "Civitas Core"
HIDE_NAVBAR_LOGO = True
CIVITAS_BRAND_LOGO_URL = "/static/assets/images/civitas-logo.svg"
CIVITAS_BRAND_COLOR = "#0369a1"
# menu_data() (superset/views/base.py) reads APP_ICON directly for the brand icon shown in the
# navbar's bootstrap payload, independent of the theme tokens set below.
APP_ICON = CIVITAS_BRAND_LOGO_URL
for _theme in (THEME_DEFAULT, THEME_DARK):
    if _theme is None:
        continue
    _theme["token"]["brandAppName"] = APP_NAME
    _theme["token"]["brandLogoAlt"] = APP_NAME
    _theme["token"]["brandLogoUrl"] = CIVITAS_BRAND_LOGO_URL
    _theme["token"]["colorPrimary"] = CIVITAS_BRAND_COLOR
    _theme["token"]["colorLink"] = CIVITAS_BRAND_COLOR
FAVICONS = [{"href": CIVITAS_BRAND_LOGO_URL, "type": "image/svg+xml"}]
