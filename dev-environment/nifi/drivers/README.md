# NiFi JDBC drivers

`PutDatabaseRecord` / `DBCPConnectionPool` need a PostgreSQL JDBC driver jar on disk.
This directory is mounted read-only into the NiFi container at `/opt/nifi/drivers`.

The `start-portal-dev.sh` script automatically downloads the driver on first run.
To download it manually:

```bash
curl -L -o postgresql.jar \
  https://jdbc.postgresql.org/download/postgresql-42.7.4.jar
```

(Any reasonably recent `postgresql-*.jar` will work.)

> **Note:** `*.jar` files in this directory are git-ignored and not committed to the repository.
