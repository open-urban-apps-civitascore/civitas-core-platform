# CIVITAS CORE Portal Backend

Spring Boot REST API with OAuth2/Keycloak integration, PostgreSQL database, and Kafka event streaming.

## 🚀 Quick Start

### Prerequisites

* Java 21, Maven 3.6+, Docker & Docker Compose

### 1. Start Infrastructure

> **Recommended:**
> For a consistent and tested local development setup, use the centralized Docker Compose in `dev-environment/backend/`.

**Option A: Automated startup**

Builds Portal Backend + Config Adapter and starts all required services.

```bash
cd ../dev-environment/backend
./start.sh
```

Alternatively, if you only want to start the services defined in Docker Compose **without building**, you can run:

```bash
docker compose up --build -d
```


**Option B: Manual startup**


This option is useful if you want to **run the Portal Backend locally** for development.

```bash
cd ../dev-environment/backend
docker compose up -d postgres-portal postgres-keycloak keycloak kafka config-adapter apisix
```

Only infrastructure services are started. The Portal Backend container is **not started** in this mode.


### 2. Run Application (local without Docker)

Once the infrastructure is running, you can start the backend on your local machine:

```bash
cd /portal-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres
```

### Access Points

* API: [http://localhost:8089/v1](http://localhost:8089/v1)
* Swagger: [http://localhost:8089/v1/swagger-ui.html](http://localhost:8089/v1/swagger-ui.html)
* Keycloak: [http://localhost:8080](http://localhost:8080)
* Kafka UI: [http://localhost:8090](http://localhost:8090)
* Config Adapter: [http://localhost:8088/health/ready](http://localhost:8088/health/ready)

---

## 🔧 Configuration

### Local Services

| Service           | URL / Host        | Port | Credentials       |
| ----------------- | ----------------- | ---- | ----------------- |
| Backend API       | localhost:8089/v1 | 8089 | –                 |
| Config Adapter    | localhost         | 8088 | –                 |
| Keycloak          | localhost         | 8080 | admin/admin       |
| App Database      | localhost         | 5432 | admin/admin       |
| Keycloak Database | localhost         | 5433 | keycloak/keycloak |
| Kafka             | localhost         | 9092 | –                 |
| Kafka UI          | localhost         | 8090 | –                 |

### Key Configuration

```yaml
# application.yaml
server:
  port: 8089
  servlet:
    context-path: /v1

# application-local.yaml
keycloak:
  realm: civitas-core
  auth-server-url: http://localhost:8080

# application-postgres.yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/portal_backend
    username: admin
    password: admin
```

### Spring Profiles

* **local** – local development
* **local-init** – on startup, creates the groups and users defined in `application-local-init.yaml` and syncs them to Keycloak (requires `local`).
* **postgres** – PostgreSQL datasource
* **debug** – extended logging

---

## 🏗️ Project Structure

```
portal-backend/
├── src/
│   ├── main/java/              # Application source
│   ├── main/resources/
│   │   ├── application.yaml
│   │   ├── application-local.yaml
│   │   ├── application-postgres.yaml
│   │   └── application-debug.yaml
│   ├── test/java/              # Unit tests
│   └── testIntegration/        # Integration tests
├── Dockerfile                  # Backend container
├── .gitlab-ci.yml              # CI/CD pipeline
└── pom.xml                     # Maven configuration

```

---

## 🧪 Testing & Development

### Essential Commands

```bash
# Development
mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres

# Testing
mvn test                    # Unit tests
mvn verify                  # Full test suite (incl. Testcontainers)

# Formatting
mvn spotless:apply          # Format code
mvn spotless:check          # Check formatting
```

### Docker Infrastructure

```bash
# Start all services
cd ../dev-environment/backend
docker compose up -d

# Stop all services
docker compose down

# Reset environment
docker compose down -v
```

---

## 🔒 Authentication

### Keycloak Setup

* **Realm:** `civitas-core`
* Automatically imported from
  `../dev-environment/keycloak/realm-export.json`
* Admin Console: [http://localhost:8080/admin](http://localhost:8080/admin) (admin/admin)

### Getting JWT Tokens

```bash
curl -X POST http://localhost:8080/realms/civitas-core/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=your-client&username=user&password=pass"
```

### Using Tokens

```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8089/v1/api/your-endpoint
```

---

## 📡 Kafka Integration

* Portal Backend publishes and consumes events via Kafka
* Kafka UI available at: [http://localhost:8090](http://localhost:8090)

---

## 🏗️ Technology Stack

* **Java 21** + Spring Boot 3.5.7
* **PostgreSQL 18** with JPA/Hibernate
* **Keycloak 26.5** (OAuth2 / OIDC)
* **Kafka** for event streaming
* **OpenAPI 3** + Swagger UI
* **Testcontainers** for integration testing
* **Maven** with Spotless, JaCoCo, Surefire/Failsafe

---

## 📚 API Documentation

* **Swagger UI:** [http://localhost:8089/v1/swagger-ui.html](http://localhost:8089/v1/swagger-ui.html)
* **OpenAPI Spec:** [http://localhost:8089/v1/v3/api-docs](http://localhost:8089/v1/v3/api-docs)
* **Health Check:** [http://localhost:8089/v1/actuator/health](http://localhost:8089/v1/actuator/health)
* **Metrics:** [http://localhost:8089/v1/actuator/prometheus](http://localhost:8089/v1/actuator/prometheus)

---

## 🔧 Troubleshooting

### Common Checks

```bash
# Check ports
lsof -i :8089 :8088 :8080 :5432 :9092

# Database access
cd ../dev-environment/backend
docker compose exec postgres-portal psql -U admin -d portal_backend -c "\dt"

# Logs
docker compose logs -f portal-backend
```

### Development Workflow

1. Start infrastructure: `cd ../dev-environment/backend && docker compose up -d`
2. Run application: `mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres`
3. Make changes
4. Test: `mvn test` or `mvn verify`
5. Format: `mvn spotless:apply`
6. Commit & push
