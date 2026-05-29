# Use JRE Alpine for small image size and security
FROM eclipse-temurin:25-jre-alpine@sha256:609c3d5184aeabe15c1a077af6d1c53d0ba65df18347c67043d00195267c4f7d

LABEL maintainer="CIVITAS/CORE Team"
LABEL description="CIVITAS/CORE Portal Backend - IoT Schema Service"
LABEL civitas=true

WORKDIR /app

# Install dependencies
ENV TZ=Europe/Berlin
RUN apk add --no-cache curl tzdata

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
