FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="CIVITAS/Interfaces Team"
LABEL description="CIVITAS/CORE Configuration Adapter"

WORKDIR /civitas

ENV TZ=Europe/Berlin
RUN apk add --no-cache curl tzdata

COPY config-adapter-application/target/*.jar ./config-adapter.jar

RUN addgroup -g 1001 -S civitas && \
    adduser -u 1001 -S civitas -G civitas && \
    chown -R civitas:civitas /civitas
USER civitas

ENV PORT=8091
EXPOSE $PORT
ENV NODE_ENV=production

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the config adapter
CMD ["sh", "-c", "java $JAVA_OPTS -jar /civitas/config-adapter.jar"]
