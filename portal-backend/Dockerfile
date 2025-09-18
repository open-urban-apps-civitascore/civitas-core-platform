# Build stage
FROM maven:3.9.8-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy POM and source code
COPY pom.xml .
COPY src/ src/

# Build application with Maven cache and extract Spring Boot layers
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests && \
    java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="CIVITAS/CORE Team"
LABEL description="CIVITAS/CORE Portal Backend - IoT Schema Service"

# Install dependencies and create user
ENV TZ=Europe/Berlin
RUN apk add --no-cache curl tzdata && \
    addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

# Switch to non-root user
USER spring:spring
WORKDIR /app

# Copy Spring Boot layers in optimal order for Docker layer caching
COPY --from=build --chown=spring:spring /app/target/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /app/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /app/target/extracted/application/ ./

# Expose application port
EXPOSE 8089

# Health check with correct endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8089/v2/actuator/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"


# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
