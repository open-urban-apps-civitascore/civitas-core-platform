#!/bin/sh
set -eu

create_database() {
    role_name=$1
    role_password=$2
    database_name=$3

    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        --set=ON_ERROR_STOP=1 \
        --set=role_name="$role_name" \
        --set=role_password="$role_password" \
        --set=database_name="$database_name" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role_name', :'role_password')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = :'role_name')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'role_name')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_database WHERE datname = :'database_name')
\gexec
SQL
}

create_database keycloak "$KEYCLOAK_DB_PASSWORD" "$KEYCLOAK_DB_NAME"
create_database sensorthings "$FROST_DB_PASSWORD" "$FROST_DB_NAME"
create_database geoserver "$GEOSERVER_DB_PASSWORD" "$GEOSERVER_DB_NAME"
create_database "$MODEL_FORGE_DB_USER" "$MODEL_FORGE_DB_PASSWORD" "$MODEL_FORGE_DB_NAME"
create_database "$NIFI_DEMO_DB_USER" "$NIFI_DEMO_DB_PASSWORD" "$NIFI_DEMO_DB_NAME"

for database_name in "$FROST_DB_NAME" "$GEOSERVER_DB_NAME" "$NIFI_DEMO_DB_NAME"; do
    psql --username "$POSTGRES_USER" --dbname "$database_name" \
        --set=ON_ERROR_STOP=1 \
        --command='CREATE EXTENSION IF NOT EXISTS postgis'
done

# Keep the local GeoServer sample tables from the previous dedicated PostGIS service.
psql --username "$POSTGRES_USER" --dbname "$GEOSERVER_DB_NAME" \
    --set=ON_ERROR_STOP=1 \
    --file=/docker-entrypoint-initdb.d/geoserver/01_schema.sql

# Keep the NiFi demo's seed schema in its own logical database on the shared server.
psql --username "$POSTGRES_USER" --dbname "$NIFI_DEMO_DB_NAME" \
    --set=ON_ERROR_STOP=1 \
    --file=/docker-entrypoint-initdb.d/nifi-demo/01-schema.sql
