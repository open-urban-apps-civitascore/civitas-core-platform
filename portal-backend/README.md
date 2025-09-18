# CIVITAS CORE Portal Backend

Spring Boot REST API with OAuth2/Keycloak integration.

## 🚀 Quick Start

### Prerequisites
- Java 21, Maven 3.6+, Docker & Docker Compose

### 1. Start Infrastructure
```bash
cd /docker/local/postgres
docker-compose -f docker-compose-psotgres.yml up -d
```

It's also necessary to start Keycloak from the 'dev-environment/keycloak' folder:
```bash
cd /civitias-core-platform/dev-environment/keycloak
docker-compose -f docker-compose.yml up -d
```

### 2. Run Application
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Access Points:**
- API: http://localhost:8089/v2
- Swagger: http://localhost:8089/v2/swagger-ui.html
- Keycloak: http://localhost:8080

## 🔧 Configuration

### Local Services
| Service | URL               | Port | 
|---------|-------------------|------|
| Backend API | localhost:8089/v2 | 8089 | 
| Keycloak | localhost:8080    | 8080 | 
| App Database | localhost:5434    | 5434 | 
| Keycloak DB | localhost:5432    | 5432 | 

### Key Configuration
```yaml
# application-local.yml
server:
  port: 8089
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/iot_schema
    username: iot
    password: iot
keycloak:
  realm: civitas-core
  auth-server-url: http://localhost:8080
```

### Project Structure
```
portal-backend/
├── src/
│   ├── main/java/          # Application source
│   ├── test/java/          # Unit tests
│   └── testIntegration/    # Integration tests
├── docker/local/keycloak/
│   ├── docker-compose-local.yml
│   └── realms/
│       └── iot-realm.json  # Keycloak realm import
├── .gitlab-ci.yml          # CI/CD pipeline
└── pom.xml                 # Maven configuration
```

## 🧪 Testing & Development

### Essential Commands
```bash
# Development
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Testing
mvn test                    # Unit tests
mvn verify                  # Full test suite
mvn spotless:apply          # Format code
mvn spotless:check          # Check formatting

# Infrastructure
docker-compose -f docker-compose-postgres.yml up -d     # Start
docker-compose -f docker-compose-postgres.yml down      # Stop
```

### CI/CD Pipeline
- **Validate**: Code formatting (Spotless) + compilation
- **Test**: Unit tests + Integration tests (Testcontainers) + Coverage merge
- **Build**: JAR packaging

## 🔒 Authentication

### Keycloak Setup

The `civitas-core` realm is automatically imported from `/civitias-core-platform/dev-environment/keycloak/realm-export.json` when starting the infrastructure.

### Getting JWT Tokens
```bash
curl -X POST http://localhost:8080/realms/iot/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=your-client&username=user&password=pass"
```

### Using Tokens
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8089/v2/api/your-endpoint
```

## 🏗️ Technology Stack

- **Java 21** + Spring Boot 3.5.5
- **PostgreSQL** with JPA/Hibernate
- **OAuth2** Resource Server (Keycloak)
- **OpenAPI 3** + Swagger UI
- **Testcontainers** for integration testing
- **Maven** with Spotless, JaCoCo, Surefire/Failsafe

## 📚 API Documentation

- **Swagger UI**: http://localhost:8089/v2/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8089/v2/api-docs
- **Health Check**: http://localhost:8089/v2/actuator/health
- **Metrics**: http://localhost:8089/v2/actuator/prometheus

## 🔧 Troubleshooting

### Common Issues
```bash
# Check ports
lsof -i :8089 :8080 :5432 :5434

# Test database
docker-compose exec postgres-portal psql -U iot -d iot_schema -c "\dt"

# Reset environment
docker-compose -f docker-compose-local.yml down -v && docker volume prune
```

### Development Workflow
1. Start infrastructure: `docker-compose -f docker-compose-local.yml up -d`
2. Run application: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
3. Make changes
4. Test: `mvn test` (quick) or `mvn verify` (full)
5. Format: `mvn spotless:apply`
6. Commit & push