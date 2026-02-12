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
# export ADAPTERS=com.civitas.configadapter.keycloak.KeycloakAdapter,com.civitas.configadapter.examples.DummyLogAdapter
# export EVENTHANDLER_CLASS=com.civitas.event.handler.kafka.KafkaEventHandler

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
java -jar config-adapter-application/target/config-adapter-application-1.1.0.jar
