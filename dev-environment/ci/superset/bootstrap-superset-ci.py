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


def ensure_sink_database(db, Database) -> None:
    """Registers the Superset "database" the saga handler's `trino.database-id` points at.

    Named after the shared Trino connection this stands in for in the full dev stack; here it
    connects straight to postgres-sink since Trino/Keycloak SSO aren't part of this test stack.
    Superset assigns sequential integer ids, so as the only database this bootstrap ever creates
    it lands on id=1 — the config-adapter default (see application.properties).
    """
    name = os.environ.get("SUPERSET_SINK_DATABASE_NAME", "geosink")
    existing = db.session.query(Database).filter(Database.database_name == name).one_or_none()
    if existing is not None:
        return

    database = Database(database_name=name)
    database.sqlalchemy_uri = os.environ["SUPERSET_SINK_SQLALCHEMY_URI"]
    database.expose_in_sqllab = True
    database.allow_dml = False
    db.session.add(database)
    db.session.commit()


def main() -> None:
    app = create_app()
    with app.app_context():
        from superset import db
        from superset.models.core import Database

        ensure_admin(app, db)
        ensure_sink_database(db, Database)


if __name__ == "__main__":
    main()
