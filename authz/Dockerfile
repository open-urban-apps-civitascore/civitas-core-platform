# AuthZ Repository - Runtime-only Docker image
#
# The JAR must be pre-built on the host before building this image:
#   mvn -f authz/repository/pom.xml package -DskipTests
#
# This is required because portal-model:1.0.0-SNAPSHOT is a local dependency
# not published to Maven Central, so multi-stage Maven builds inside Docker
# cannot resolve it. Building on the host (where ~/.m2 has it) works fine.
#
# The cold-start.sh script handles this automatically.

FROM eclipse-temurin:25-jre-alpine AS authz-repo-service

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy pre-built JAR
COPY ./authz/repository/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8091

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8091/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

FROM alpine:3.23.3 AS fetcher
COPY authz/rego/bundle.tar.gz /bundle.tar.gz
RUN mkdir /out && \
    tar -xzf /bundle.tar.gz -C /out && \
    chmod -R a+rX /out && \
    printf '{\n  "authz_repository_url": "http://civitas-authz-repository:8091/api/v1/user-context",\n  "dataset_pool_membership_url": "http://civitas-authz-repository:8091/api/v1/dataset-pool",\n  "authz_cache_duration_seconds": 0\n}\n' > /out/config.json


FROM openpolicyagent/opa:1.14.0-static AS opa

COPY --from=fetcher /out/config.json /data/config.json
COPY --from=fetcher /out/bundle/policy /policies
COPY --from=fetcher /out/bundle/lib /helpers
COPY --from=fetcher /out/bundle/providers /providers
COPY --from=fetcher /out/data.json /data/data.json

HEALTHCHECK --interval=10s --timeout=5s --retries=3 --start-period=5s \
    CMD /opa eval -f pretty true || exit 1

CMD [ "run", "--server", "--addr=0.0.0.0:8181", "--log-level=info", "--log-format=json", "--set=decision_logs.console=true", "/policies", "/helpers", "/providers", "/data" ]