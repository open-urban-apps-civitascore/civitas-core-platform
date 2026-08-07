import os


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
