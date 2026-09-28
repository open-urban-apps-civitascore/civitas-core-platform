# Pinned: eclipse-temurin:25-jre-alpine (2026-09-24), Alpine 3.24.2. Update digest on base image updates.
# Bumps libexpat to 2.8.4-r0 (CVE-2026-76641, CVE-2026-66046, CVE-2026-76956, CVE-2026-76957)
# and openssl/libssl3/libcrypto3 to 3.5.8-r0 (CVE-2026-14456).
FROM eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8

LABEL maintainer="CIVITAS/Interfaces Team"
LABEL description="CIVITAS/CORE Configuration Adapter"
LABEL civitas=true

WORKDIR /civitas

ENV TZ=Europe/Berlin
# gcompat: snappy-java ships a glibc-linked native library; without the shim the Kafka
# consumer cannot read snappy-compressed messages on this musl-based image
# libexpat: force the patched package (CVE-2026-93990) since the pinned base image
# still bundles a vulnerable version; drop this once the base digest above ships the fix
RUN apk add --no-cache --upgrade "libexpat=2.8.5-r0" && \
    apk add --no-cache "curl=8.22.0-r0" "tzdata=2026d-r0" "gcompat=1.1.0-r4"

COPY config-adapter-application/target/config-adapter-application-*.jar ./config-adapter.jar

RUN addgroup -g 1001 -S civitas && \
    adduser -u 1001 -S civitas -G civitas && \
    chown -R civitas:civitas /civitas
USER civitas

ENV PORT=8089
EXPOSE $PORT

# Health check with correct endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8089/health/ready || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the config adapter
CMD ["sh", "-c", "java $JAVA_OPTS -jar /civitas/config-adapter.jar"]
