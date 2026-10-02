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

# Pinned: eclipse-temurin:25-jre-alpine (2026-09-24), Alpine 3.24.2. Update digest on base image updates.
# Bumps libexpat to 2.8.4-r0 (CVE-2026-76641, CVE-2026-66046, CVE-2026-76956, CVE-2026-76957)
# and openssl/libssl3/libcrypto3 to 3.5.8-r0 (CVE-2026-14456).
FROM eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8 AS authz-repo-service

WORKDIR /app

# libexpat: force the patched package (CVE-2026-93990) since the pinned base image
# still bundles a vulnerable version; drop this once the base digest above ships the fix
RUN apk add --no-cache --upgrade "libexpat=2.8.5-r0"

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

# Pinned: alpine:3.23.5 (2026-09-24). Update digest on base image updates.
FROM alpine:3.23.5@sha256:fd791d74b68913cbb027c6546007b3f0d3bc45125f797758156952bc2d6daf40 AS fetcher
COPY authz/rego/bundle.tar.gz /bundle.tar.gz
RUN mkdir /out && \
    tar -xzf /bundle.tar.gz -C /out && \
    chmod -R a+rX /out && \
    printf '{\n  "authz_repository_url": "http://civitas-authz-repository:8091/api/v1/user-context",\n  "dataset_pool_membership_url": "http://civitas-authz-repository:8091/api/v1/dataset-pool",\n  "authz_cache_duration_seconds": 0\n}\n' > /out/config.json


# Pinned: openpolicyagent/opa:1.21.0-static (2026-09-24). Update digest on base image updates.
# Bumps golang.org/x/crypto to v0.55.0 (CVE-2026-56854) and google.golang.org/grpc
# to v1.83.2 (CVE-2026-84304, CVE-2026-84445).
FROM openpolicyagent/opa:1.21.0-static@sha256:9eb36ac3ceb3b855e776e60926a8a5142f96109a219a5fcd490fa1744c755bec AS opa

COPY --from=fetcher /out/config.json /data/config.json
COPY --from=fetcher /out/bundle/policy /policies
COPY --from=fetcher /out/bundle/lib /helpers
COPY --from=fetcher /out/bundle/providers /providers
COPY --from=fetcher /out/data.json /data/data.json

HEALTHCHECK --interval=10s --timeout=5s --retries=3 --start-period=5s \
    CMD /opa eval -f pretty true || exit 1

CMD [ "run", "--server", "--addr=0.0.0.0:8181", "--log-level=info", "--log-format=json", "--set=decision_logs.console=true", "/policies", "/helpers", "/providers", "/data" ]