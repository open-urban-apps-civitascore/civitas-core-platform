#!/bin/bash

echo "Building the multi-module project..."
mvn clean package

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Starting the Keycloak Config Adapter..."
java -jar config-adapter-application/target/config-adapter-application-1.0.0-SNAPSHOT.jar
