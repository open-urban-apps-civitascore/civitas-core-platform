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


def main() -> None:
    app = create_app()
    with app.app_context():
        from superset import db

        ensure_admin(app, db)


if __name__ == "__main__":
    main()
