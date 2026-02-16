# AuthZ Repository - Runtime-only Docker image
#
# The JAR must be pre-built on the host before building this image:
#   mvn -f authz/repository/pom.xml package -DskipTests
#
# This is required because portal-model:1.0.0-SNAPSHOT is a local dependency
# not published to Maven Central, so multi-stage Maven builds inside Docker
# cannot resolve it. Building on the host (where ~/.m2 has it) works fine.
#
# The start-dev.sh script handles this automatically.

# Pinned: eclipse-temurin:21-jre-alpine (2026-02-16). Update digest on base image updates.
FROM eclipse-temurin:21-jre-alpine@sha256:6ad8ed080d9be96b61438ec3ce99388e294af216ed57356000c06070e85c5d5d

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy pre-built JAR
COPY target/*.jar app.jar

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8091

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8091/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
