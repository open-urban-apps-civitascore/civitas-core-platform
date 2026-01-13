# CIVITAS CORE Portal Backend

Spring Boot REST API with OAuth2/Keycloak authentication, PostgreSQL database, and Kafka event streaming.

---

## 🚀 Quick Start

### Option 1: Automated (Recommended)
```bash
./start.sh
```
Builds Portal Backend + Config Adapter, starts all services via Docker Compose.

### Option 2: Manual

**Prerequisites:** Java 21, Maven 3.6+, Docker, Docker Compose

**Start all services:**
```bash
docker-compose up --build
```

**Access endpoints:**
- Portal Backend API: http://localhost:8089/v2
- Swagger UI: http://localhost:8089/v2/swagger-ui.html
- Health Check: http://localhost:8089/v2/actuator/health
- Config Adapter: http://localhost:8088/health/ready
- Keycloak Admin: http://localhost:8080 (admin/admin)
- Kafka UI: http://localhost:8090
- PostgreSQL Portal: localhost:5432 (admin/admin)
- PostgreSQL Keycloak: localhost:5433 (keycloak/keycloak)

---

## 📋 Services Overview

| Service | Port | Purpose | Credentials |
|---------|------|---------|-------------|
| **portal-backend** | 8089 | Main REST API | - |
| **config-adapter** | 8088 | Configuration service | - |
| **keycloak** | 8080 | OAuth2/OIDC authentication | admin/admin |
| **postgres-portal** | 5432 | Application database | admin/admin |
| **postgres-keycloak** | 5433 | Keycloak database | keycloak/keycloak |
| **kafka** | 9092 | Event streaming | - |
| **zookeeper** | 2181 | Kafka coordination | - |
| **kafka-ui** | 8090 | Kafka management UI | - |

---

## ⚙️ Configuration

### Spring Profiles
- **local**: Local development settings
- **postgres**: PostgreSQL database configuration  
- **debug**: Enhanced logging and debugging

### Key Configuration Files
```yaml
# application.yaml (base configuration)
server:
  port: 8089
  servlet:
    context-path: /v2

# application-local.yaml
keycloak:
  realm: master
  auth-server-url: http://localhost:8080

# application-postgres.yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/portal_backend
    username: admin
    password: admin
```

### Environment Variables (Docker)
See `docker-compose.yml` for complete list:
- `SPRING_DATASOURCE_URL`: Database connection string
- `KEYCLOAK_AUTH_SERVER_URL`: Keycloak server URL
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker addresses
- `SPRING_PROFILES_ACTIVE`: Active Spring profiles

---

## 🛠️ Development

### Local Development (without Docker)

**Option A: Start infrastructure from portal-backend folder:**
```bash
# Start databases, Kafka, Keycloak
docker-compose up -d postgres-portal postgres-keycloak kafka zookeeper keycloak
```

**Option B: Start infrastructure from dev-environment (individual services):**
```bash
# PostgreSQL
cd ../dev-environment/postgres && docker-compose up -d

# Keycloak
cd ../dev-environment/keycloak && docker-compose up -d

# Kafka
cd ../dev-environment/kafka && docker-compose up -d
```

**Run application locally:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres,debug
```

**Stop infrastructure:**
```bash
docker-compose down
```

### Build Commands

```bash
# Build JAR (skip tests)
mvn clean package -DskipTests

# Build + run unit tests
mvn clean package

# Build + run all tests (integration tests with Testcontainers)
mvn clean verify

# Code formatting
mvn spotless:apply          # Auto-format
mvn spotless:check          # Check only
```

### Docker Commands

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f portal-backend
docker-compose logs -f config-adapter

# Restart single service
docker-compose restart portal-backend

# Stop all services
docker-compose down

# Reset completely (removes volumes)
docker-compose down -v
```

---

## 🔒 Authentication

### Keycloak Realm
- **Realm:** `civitas-core` (auto-imported from `../dev-environment/keycloak/realm-export.json`)
- **Local config uses:** `master` realm (see `application-local.yaml`)
- **Admin Console:** http://localhost:8080/admin (admin/admin)

### Get Access Token

```bash
curl -X POST http://localhost:8080/realms/civitas-core/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=your-client-id" \
  -d "username=your-username" \
  -d "password=your-password"
```

### Use Token in API Requests

```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8089/v2/api/endpoint
```

---

## 📡 Kafka Integration

### Topics
- Portal Backend publishes/consumes events via Kafka
- View topics in Kafka UI: http://localhost:8090

### Manual Consumer (for debugging)
```bash
docker-compose --profile consumer up kafka-consumer
```

---

## 🏗️ Architecture

### Technology Stack
- **Java 21** + Spring Boot 3.5.5
- **PostgreSQL 15** with Hibernate/JPA
- **Keycloak 26.5** (OAuth2/OIDC Resource Server)
- **Kafka 7.6.0** (Event streaming)
- **Maven** (Build tool)
- **Testcontainers** (Integration testing)
- **Spotless** (Code formatting)

### Project Structure
```
portal-backend/
├── src/
│   ├── main/
│   │   ├── java/              # Application code
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-local.yaml
│   │       ├── application-postgres.yaml
│   │       └── application-debug.yaml
│   ├── test/                  # Unit tests
│   └── testIntegration/       # Integration tests
├── docker-compose.yml         # All services definition
├── Dockerfile                 # Portal Backend container
├── start.sh                   # Automated startup script
├── pom.xml                    # Maven dependencies
└── README.md
```

---

## 🔍 Troubleshooting

### Check Service Health
```bash
# Portal Backend
curl http://localhost:8089/v2/actuator/health

# Config Adapter
curl http://localhost:8088/health/ready

# Keycloak
curl http://localhost:8080/health/ready
```

### Check Port Usage
```bash
lsof -i :8089  # Portal Backend
lsof -i :8088  # Config Adapter
lsof -i :8080  # Keycloak
lsof -i :5432  # PostgreSQL Portal
lsof -i :9092  # Kafka
```

### Database Access
```bash
# Portal database
docker-compose exec postgres-portal psql -U admin -d portal_backend

# List tables
\dt

# Check connections
SELECT * FROM pg_stat_activity;
```

### View Container Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f portal-backend

# Last 100 lines
docker-compose logs --tail=100 portal-backend
```

### Reset Environment
```bash
# Stop and remove containers + volumes
docker-compose down -v

# Remove dangling volumes
docker volume prune -f

# Rebuild from scratch
./start.sh
```

### Common Issues

**Port already in use:**
```bash
# Find process using port
lsof -ti:8089 | xargs kill -9
```

**Build fails:**
```bash
# Clear Maven cache
mvn clean
rm -rf ~/.m2/repository
```

**Database connection issues:**
```bash
# Restart database
docker-compose restart postgres-portal

# Check database logs
docker-compose logs postgres-portal
```

---

## 📚 API Documentation

- **Swagger UI:** http://localhost:8089/v2/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8089/v2/v3/api-docs
- **Actuator Health:** http://localhost:8089/v2/actuator/health
- **Actuator Metrics:** http://localhost:8089/v2/actuator/prometheus

---

## 🧪 Testing

### Test Levels
- **Unit Tests:** No external dependencies (~10 tests, <1 min)
- **Integration Tests:** Testcontainers with real databases (~220 tests, ~5 min)

### Run Tests
```bash
# Unit tests only
mvn test

# All tests (unit + integration)
mvn verify

# With coverage report
mvn clean verify
# Report: target/site/jacoco-merged/index.html
```

### CI/CD Pipeline
Automated via GitLab CI (`.gitlab-ci.yml`):
1. **Validate**: Code formatting (Spotless) + compilation
2. **Test**: Unit tests + Integration tests (Testcontainers)
3. **Coverage**: JaCoCo merge report
4. **Build**: JAR packaging

---

## 🚢 Deployment

### Build JAR
```bash
mvn clean package -DskipTests
# Output: target/portal-backend-0.0.1-SNAPSHOT.jar
```

### Run JAR
```bash
java -jar target/portal-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

### Docker Image
```bash
# Build
docker build -t civitas-portal-backend:latest .

# Run
docker run -p 8089:8089 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
  -e KEYCLOAK_AUTH_SERVER_URL=http://keycloak:8080 \
  civitas-portal-backend:latest
```

---
