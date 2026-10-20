import os

from superset.app import create_app


def ensure_admin(app, db) -> None:
    sm = app.appbuilder.sm
    username = os.environ["SUPERSET_ADMIN_USERNAME"]
    if sm.find_user(username=username) is not None:
        return

    admin_role = sm.find_role("Admin")
    sm.add_user(
        username,
        "CI",
        "Admin",
        f"{username}@civitas.local",
        admin_role,
        os.environ["SUPERSET_ADMIN_PASSWORD"],
    )
    db.session.commit()


def ensure_trino_database(db, Database) -> None:
    """Registers the Trino connection used by the config-adapter Superset saga handler.

    Superset assigns sequential integer ids, so this must stay the first connection (id=1) — the
    config-adapter default (see application.properties). Rename the old direct-PostgreSQL entry
    when it exists, so a reused metadata database keeps the configured id.
    """
    name = os.environ.get("SUPERSET_TRINO_DATABASE_NAME", "CORE")
    database = db.session.query(Database).filter(Database.database_name == name).one_or_none()
    legacy = None
    if name == "CORE":
        legacy_name = os.environ.get("SUPERSET_LEGACY_DATABASE_NAME", "geosink")
        legacy = (
            db.session.query(Database)
            .filter(Database.database_name == legacy_name)
            .one_or_none()
        )
        if legacy is not None and legacy.id == 1 and database is not None and database.id != 1:
            # A prior test bootstrap may have created CORE as id=2 after geosink already occupied
            # id=1. Drop only that duplicate test connection and reuse the legacy id-1 row.
            db.session.delete(database)
            db.session.flush()
            database = legacy
        if database is None:
            database = legacy
        if database is not None and database is legacy:
            database.database_name = name

    if database is None:
        database = Database(database_name=name)
    if database.id is not None and database.id != 1:
        raise RuntimeError(
            f"Superset {name} connection must have id=1 for the config-adapter; "
            f"found id={database.id}"
        )
    database.sqlalchemy_uri = os.environ["SUPERSET_TRINO_SQLALCHEMY_URI"]
    database.expose_in_sqllab = True
    database.allow_dml = False
    database.allow_ctas = False
    database.allow_cvas = False
    database.allow_run_async = False
    database.impersonate_user = True
    db.session.add(database)
    db.session.commit()


def main() -> None:
    app = create_app()
    with app.app_context():
        from superset import db
        from superset.models.core import Database

        ensure_admin(app, db)
        ensure_trino_database(db, Database)


if __name__ == "__main__":
    main()
