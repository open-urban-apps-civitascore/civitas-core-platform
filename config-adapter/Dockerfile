# Pinned: eclipse-temurin:25-jre-alpine (2026-08-21). Update digest on base image updates.
FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682

LABEL maintainer="CIVITAS/Interfaces Team"
LABEL description="CIVITAS/CORE Configuration Adapter"
LABEL civitas=true

WORKDIR /civitas

ENV TZ=Europe/Berlin
# gcompat: snappy-java ships a glibc-linked native library; without the shim the Kafka
# consumer cannot read snappy-compressed messages on this musl-based image
RUN apk add --no-cache curl tzdata gcompat

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
