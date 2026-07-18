# Model Forge Admin UI — registry console (Wicket + Spring Boot).
# Runs the pre-built fat JAR against portal-backend's database (the model_forge schema),
# so the console shows exactly what the platform created. Started by dev-environment/start-portal-dev.sh
# as the "model-forge-admin-ui" service in dev-environment/apps/docker-compose.yml.
FROM eclipse-temurin:25-jre-alpine

LABEL maintainer="CIVITAS/CORE Team"
LABEL description="CIVITAS/CORE Model Forge Admin UI - registry console"
LABEL civitas=true

WORKDIR /app

ENV TZ=Europe/Berlin
RUN apk add --no-cache curl tzdata

# Pre-built Spring Boot fat JAR (mvn package -> spring-boot repackage).
COPY target/*.jar ./app.jar

RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring && \
    chown -R spring:spring /app
USER spring

ENV PORT=8092
EXPOSE $PORT

# --add-opens java.base/java.io: Wicket's DEVELOPMENT-mode CheckingObjectOutputStream reflects into
# JDK internals to pinpoint non-serializable page state; JPMS blocks that by default on Java 17+
# (mirrors the spring-boot-maven-plugin jvmArguments in this module's pom.xml).
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom --add-opens java.base/java.io=ALL-UNNAMED"

# No actuator on this module — probe the Wicket home page instead.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD curl -f http://localhost:8092/ || exit 1

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
