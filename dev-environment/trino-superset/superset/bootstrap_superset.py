import json
import os

from superset.app import create_app


def ensure_admin(app, db) -> None:
    sm = app.appbuilder.sm
    username = os.environ["SUPERSET_ADMIN_USERNAME"]
    user = sm.find_user(username=username)
    if user is not None:
        return

    admin_role = sm.find_role("Admin")
    sm.add_user(
        username,
        os.environ["SUPERSET_ADMIN_FIRSTNAME"],
        os.environ["SUPERSET_ADMIN_LASTNAME"],
        os.environ["SUPERSET_ADMIN_EMAIL"],
        admin_role,
        os.environ["SUPERSET_ADMIN_PASSWORD"],
    )
    db.session.commit()


def ensure_service_user(app, db) -> None:
    """Create a local Superset user for automated API access (e.g. config-adapter).

    The username is the Keycloak sub UUID of a service identity that has
    TENANT-scoped DATASET_PAYLOAD_READ in the AuthZ Repository.  When Superset
    sends X-Trino-User: <username> to Trino (impersonate_user=True), OPA resolves
    the UUID to a valid user context with data permissions and allows the query.
    """
    sm = app.appbuilder.sm
    username = os.environ.get("SUPERSET_SERVICE_USERNAME")
    password = os.environ.get("SUPERSET_SERVICE_PASSWORD")
    if not username or not password:
        return

    if sm.find_user(username=username) is not None:
        return

    admin_role = sm.find_role("Admin")
    sm.add_user(
        username,
        "Service",
        "Account",
        f"{username}@civitas.local",
        admin_role,
        password,
    )
    db.session.commit()


def ensure_database(db, Database, name: str, sqlalchemy_uri: str, extra: dict):
    database = (
        db.session.query(Database)
        .filter(Database.database_name == name)
        .one_or_none()
    )

    if database is None:
        database = Database(database_name=name)
        database.sqlalchemy_uri = sqlalchemy_uri
        database.extra = json.dumps(extra)
        database.expose_in_sqllab = True
        database.allow_ctas = False         # no CREATE TABLE AS SELECT
        database.allow_cvas = False         # no CREATE VIEW AS SELECT
        database.allow_dml = False          # no INSERT / UPDATE / DELETE
        database.allow_run_async = False    # queries run synchronously (no Celery needed)
        database.force_ctas_schema = None   # no schema override for CTAS results
        database.impersonate_user = True    # forward username to Trino via X-Trino-User
        db.session.add(database)
        db.session.commit()

    return database


def main() -> None:
    app = create_app()
    with app.app_context():
        from superset import db
        from superset.models.core import Database

        ensure_admin(app, db)
        ensure_service_user(app, db)

        trino_database = ensure_database(
            db,
            Database,
            os.environ["SUPERSET_TRINO_DATABASE_NAME"],
            (
                "trino://"
                f"{os.environ['TRINO_SUPERSET_SERVICE_USER']}:{os.environ['TRINO_SUPERSET_SERVICE_PASSWORD']}"
                "@trino:8446/civitas/public"
            ),
            {
                "metadata_params": {},
                "engine_params": {
                    "connect_args": {
                        "http_scheme": "https",
                        "verify": False,
                    }
                },
                "metadata_cache_timeout": {},
                "schemas_allowed_for_file_upload": [],
            },
        )


if __name__ == "__main__":
    main()
