# NiFi JDBC drivers

`PutDatabaseRecord` / `DBCPConnectionPool` need a PostgreSQL JDBC driver jar on disk.
This directory is mounted read-only into the NiFi container at `/opt/nifi/drivers`.

The `start-portal-dev.sh` script copies the drivers from `nifi-extensions/nifi-jdbc-drivers`.
To copy them manually:

```bash
mvn -f ../../../nifi-extensions/pom.xml package -DskipTests
cp ../../../nifi-extensions/nifi-jdbc-drivers/target/drivers/*.jar .
```

> **Note:** `*.jar` files in this directory are git-ignored and not committed to the repository.
