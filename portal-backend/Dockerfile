# Build stage - use official Maven image
FROM maven:3.9.8-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy and build
COPY pom.xml .
COPY src/ src/
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
