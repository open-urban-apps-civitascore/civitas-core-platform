# Use JRE Alpine for small image size and security
# Pinned: eclipse-temurin:25-jre-alpine (2026-09-24), Alpine 3.24.2. Update digest on base image updates.
# Bumps libexpat to 2.8.4-r0 (CVE-2026-76641, CVE-2026-66046, CVE-2026-76956, CVE-2026-76957)
# and openssl/libssl3/libcrypto3 to 3.5.8-r0 (CVE-2026-14456).
FROM eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8

LABEL maintainer="CIVITAS/CORE Team"
LABEL description="CIVITAS/CORE Portal Backend - IoT Schema Service"
LABEL civitas=true

WORKDIR /app

# Install dependencies
# gcompat: snappy-java ships a glibc-linked native library; without the shim the Kafka
# producer (compression=snappy) cannot load it on Alpine/musl and every publish fails.
# libexpat: force the patched package (CVE-2026-93990) since the pinned base image
# still bundles a vulnerable version; drop this once the base digest above ships the fix
ENV TZ=Europe/Berlin
RUN apk add --no-cache --upgrade "libexpat=2.8.5-r0" && \
    apk add --no-cache "curl=8.22.0-r0" "tzdata=2026d-r0" "gcompat=1.1.0-r4"

# Copy pre-built JAR from CI artifacts
COPY target/*.jar ./app.jar

# Create non-root user for security
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring && \
    chown -R spring:spring /app
USER spring

ENV PORT=8089
EXPOSE $PORT
ENV NODE_ENV=production

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check with correct endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8089/v1/actuator/health || exit 1

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
