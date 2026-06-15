#!/bin/bash

echo "Building the multi-module project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Starting the Keycloak Config Adapter..."

# Environment variables can override application.properties
# Uncomment and modify as needed:

# Health Check Configuration
# export HEALTHCHECK_PORT=8080

# Adapter Configuration
# export ADAPTERS=de.civitascore.configadapter.keycloak.KeycloakAdapter,de.civitascore.configadapter.examples.DummyLogAdapter
# export EVENTHANDLER_CLASS=de.civitascore.event.handler.kafka.KafkaEventHandler

# Kafka Configuration
# export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
# export KAFKA_GROUP_ID=config-adapter-group

# Keycloak Configuration
# export KEYCLOAK_URL=http://localhost:8080
# export KEYCLOAK_REALM=master
# export KEYCLOAK_USERNAME=admin
# export KEYCLOAK_PASSWORD=admin
# export KEYCLOAK_CLIENT_ID=admin-cli
# export KEYCLOAK_TOPICS=de.civitascore.idm.user.created,de.civitascore.idm.user.updated,de.civitascore.idm.user.deleted

# DummyLogAdapter Configuration
# export DUMMYLOG_TOPICS=de.civitascore.idm.user.created,de.civitascore.idm.user.updated,de.civitascore.idm.user.deleted

export HEALTHCHECK_PORT=8089
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# ─── Dev-only credentials ────────────────────────────────────────────────────
# Credentials are NOT baked into the image / application.properties (security guideline).
# These dev defaults are injected here for local runs only; real deployments supply their
# own values (or ENC(...) values decrypted with CIVITAS_MASTER_KEY).
export KEYCLOAK_PASSWORD=admin
export APISIX_ADMIN_KEY=edd1c9f034335f136f87ad84b625c8f1
# Dev-only public API host (issue #1368). Required — the APISIX saga handler fails fast when
# unset. Values mirror dev-environment/start-portal-dev.sh; api.localhost needs a matching
# /etc/hosts entry: 127.0.0.1 api.localhost
export APISIX_API_HOST=api.localhost
export APISIX_API_PUBLIC_URL=http://api.localhost:9080
export APISIX_PLUGIN_CONFIG_ID=1
export APISIX_FROST_API_KEY=dev-frost-api-key
export FROST_API_KEY=dev-frost-api-key
export FLOWABLE_JDBC_PASSWORD=flowable
export GEOSERVER_ADMIN_PASSWORD=geoserver
export GEOSERVER_POSTGIS_PASSWORD=geo

java -jar config-adapter-application/target/config-adapter-application-1.1.0.jar
