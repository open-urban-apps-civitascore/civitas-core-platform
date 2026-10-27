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


def migrate_legacy_dataset_catalogs(
    db, SqlaTable, database, old_catalog: str, new_catalog: str
) -> int:
    """Move CORE datasets from a legacy Trino catalog name to its current name."""
    datasets = (
        db.session.query(SqlaTable)
        .filter(
            SqlaTable.database_id == database.id,
            SqlaTable.catalog == old_catalog,
        )
        .all()
    )
    for dataset in datasets:
        dataset.catalog = new_catalog
    if datasets:
        db.session.commit()
    return len(datasets)


def main() -> None:
    app = create_app()
    with app.app_context():
        from superset import db
        from superset.models.core import Database

        ensure_admin(app, db)
        ensure_service_user(app, db)
        # The Superset Config Adapter owns the CORE Trino connection and the narrow
        # dataset-creator role. Keep only the one-time local user bootstrap here.
        trino_database = (
            db.session.query(Database)
            .filter(Database.database_name.in_(("CORE", "Trino")))
            .one_or_none()
        )
        if trino_database is not None:
            from superset.connectors.sqla.models import SqlaTable

            migrate_legacy_dataset_catalogs(
                db, SqlaTable, trino_database, "civitas", "geodata"
            )
            migrate_legacy_dataset_catalogs(
                db, SqlaTable, trino_database, "frost", "sensordata"
            )


if __name__ == "__main__":
    main()
