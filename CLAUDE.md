# CLAUDE.md

Quick reference for Claude Code when working with CIVITAS CORE Platform v2.0.

**Detailed Guides**: See `/docs/claude/` for comprehensive documentation on specific topics.

## Repository Overview

Monorepo with three main components:
- **portal-frontend**: Next.js 15 + React 19 + Keycloak auth
- **portal-backend**: Spring Boot 3.5.7 + PostgreSQL + OAuth2
- **config-adapter**: Event-driven Kafka/CloudEvents framework

All components use Keycloak for authentication.

## Quick Start

### Infrastructure (Required)
```bash
# PostgreSQL
cd dev-environment/postgres && docker compose up -d

# Keycloak (admin/admin @ http://localhost:8080)
cd dev-environment/keycloak && docker compose up -d
```

### Portal Frontend
```bash
cd portal-frontend
pnpm install
cp .env.local.template .env.local  # Edit to add KEYCLOAK_CLIENT_SECRET

pnpm dev        # http://localhost:3000
pnpm test       # Unit tests
pnpm lint       # ESLint check
pnpm format     # Format code
```

### Portal Backend
```bash
cd portal-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# http://localhost:8089/v2
# Swagger: http://localhost:8089/v2/swagger-ui.html
```

### Config Adapter
```bash
cd config-adapter
mvn clean install
cd config-adapter-application
java -jar target/config-adapter-application-1.0.0-SNAPSHOT.jar
```

## Core Architecture

### Platform Principles (v2.0)

1. **Model-Driven**: Models are the authoritative source for data and orchestration
2. **Distributed Yet Unified**: Decoupled components, central UI for user interactions
3. **Modularity**: Minimal core, interchangeable domain components
4. **Well-Defined Interfaces**: Standard interfaces (OGC, REST, MQTT)
5. **Open Source First**: All artifacts must be published
6. **Cloud-Native**: Containerized, horizontally scalable, Kubernetes-compatible
7. **Standard Solutions Priority**: Evaluate existing components before custom dev
8. **Self-Contained**: Full operation within single Kubernetes cluster
9. **Technology Consistency**: Stable technologies, minimal diversity
10. **Multi-Tenancy**: Secure, efficient multi-tenant operations
11. **Security & Privacy by Design**: Best practices from the outset

### Backend Six-Layer Architecture

Strict downward dependency flow (API → Business → Data → Domain):

1. **API Layer**: Controllers (extend `BaseController`, thin delegation)
2. **Response Layer**: Assemblers (shape client responses)
3. **Business Layer**: Services (business rules, extend `BaseService`)
4. **Conversion Layer**: MapStruct mappers (DTOs ↔ entities)
5. **Data Access Layer**: JPA repositories with specifications
6. **Domain Model**: Entities (extend `BaseEntity`/`NamedEntity`/`ScopedEntity`), DTOs

**Implementation Flow**: Model → Data Access → Mapping → Business → Response → API

See [Backend Architecture](/docs/claude/BACKEND_ARCHITECTURE.md) for details.

### Frontend Architecture

**Stack**: Next.js 15 App Router, React 19, TypeScript, TailwindCSS 4, NextAuth 5, next-intl

**Key Patterns**:
- BFF authentication with HTTP-only cookies (never expose tokens to client)
- API routes in `src/app/api/` proxy to backend
- Components in own directories with index files
- `react-hook-form` + Zod for forms
- `@tanstack/react-table` for tables
- next-intl for i18n (`useTranslations()` hook)

See [Frontend Architecture](/docs/claude/FRONTEND_ARCHITECTURE.md) for details.

### Config Adapter

**Event-Driven Framework**: Kafka → CloudEventProcessor → ConfigAdapter → Keycloak API

**Modules**:
1. `config-adapter-api` - Core interfaces
2. `config-adapter-configuration` - Config implementation
3. `event-handler-kafka` - Kafka consumer/publisher
4. `config-adapter-application` - ServiceLoader runner
5. `config-adapter-keycloak` - Production adapter

**Creating Adapters**: Extend `AbstractConfigAdapter`, register via ServiceLoader

## Security (TR-03187 Compliant)

**CRITICAL**:
- **NEVER** implement custom cryptography (AR-12)
- **NEVER** use production data in dev/test environments (ORG-7)
- **NEVER** expose tokens to client-side (use HTTP-only cookies)
- Use prepared statements/ORMs (never string concatenation for queries)
- Validate inputs with Jakarta Bean Validation
- Use `UUID.randomUUID()` for session IDs, `SecureRandom` for random numbers
- Cryptographic algorithms must comply with BSI TR-02102-*

**Authentication**: All components use Keycloak (OAuth2/OIDC + PKCE)

**Authorization**: RBAC with scope-based inheritance (tenant → dataspace → dataset)

See [Security Guide](/docs/claude/SECURITY.md) for full details.

## Code Style & Quality

**Frontend**:
- camelCase (variables/functions), PascalCase (components), kebab-case (folders)
- Single quotes, no semicolons, 120 char max
- Format before commit: `pnpm format`

**Backend**:
- UpperCamelCase (classes), lowerCamelCase (methods), SCREAMING_SNAKE_CASE (constants)
- Google Java Format enforced via Spotless
- Format before commit: `mvn spotless:apply`
- No wildcard imports, use try-with-resources

See [Code Style Guide](/docs/claude/CODE_STYLE.md) for complete conventions.

## Testing

**Frontend**: Vitest (unit), Playwright (E2E)
**Backend**: JUnit + Testcontainers (integration tests in `src/testIntegration/`)
**Config Adapter**: JUnit + Testcontainers

```bash
# Frontend
pnpm test                              # Unit tests
pnpm test:e2e                          # E2E tests (requires Keycloak)

# Backend
mvn test                               # Unit tests
mvn verify                             # Full suite
mvn test -Dtest=ClassName#methodName   # Single test

# Config Adapter
mvn test -pl config-adapter-keycloak   # Single module
```

See [Testing Guide](/docs/claude/TESTING.md) for best practices.

## CI/CD

GitLab CI: `lint` → `test` → `qa` → `build` → `deploy`

- Path-based job filtering (only run when relevant files change)
- Multi-arch Docker images (amd64/arm64)
- Security: Secret detection, SAST, dependency scanning

Local testing: `gitlab-ci-local <jobName>`

See [CI/CD Guide](/docs/claude/CICD.md) for pipeline details.

## Development Patterns

### RESTful APIs
- OpenAPI 3 spec required from start (source of truth)
- HATEOAS links in responses
- Consistent error handling
- Proper HTTP status codes

### Data Access
- Backend: Query specifications for filtering
- Flyway migrations in `src/main/resources/db/migration/`
- **CRITICAL**: Never modify existing migrations, always create new

### Event-Driven
- CloudEvents format: `core.civitas.idm.{resource}.{action}`
- Correlation tracking for event chains
- Idempotent handlers

### Configuration
- Environment variables: UPPERCASE_UNDERSCORE
- Keep secrets out of version control (use `.env.local` templates)
- Secure by default

See [Development Patterns](/docs/claude/DEVELOPMENT_PATTERNS.md) for comprehensive guide.

## Important Reminders

- **Keycloak required**: Both frontend and backend need Keycloak running
- **Database migrations**: Never modify existing Flyway migrations
- **Code formatting**: Always run formatters before committing
- **E2E tests**: Require Keycloak setup and credentials in `.env.local`
- **Testcontainers**: Backend/adapter integration tests require Docker
- **Production builds**: Build against `pnpm-lock.yaml`, not `package.json`

## Technology Versions

- **Node**: 22 | **Java**: 21 | **pnpm**: via corepack | **Maven**: 3.9+
- **Next.js**: 15.5.0 | **React**: 19.1.0 | **Spring Boot**: 3.5.7
- **PostgreSQL**: Latest (Docker) | **Keycloak**: Latest (Docker)

## Additional Resources

**Official Documentation (v2.0)**: https://docs.core.civitasconnect.digital/review-arch-v2-doc/docs_v2/intro

**Key Sections**:
- Development: `/docs_v2/Development/intro`
- Architecture Principles: `/docs_v2/Architecture/Architecture_General/Architecture_Principles`
- Security: `/docs_v2/Architecture/Architecture_General/Security_Architecture_Principles`
- ADRs: `/docs_v2/Architecture/Architecture_Decisions/`

**GitLab**: https://gitlab.com/civitas-connect/civitas-core/

**Website**: https://www.civitasconnect.digital/civitas-core/

## Detailed Documentation

- [Backend Architecture](/docs/claude/BACKEND_ARCHITECTURE.md) - Six-layer pattern, packages, implementation flow
- [Frontend Architecture](/docs/claude/FRONTEND_ARCHITECTURE.md) - Stack, directories, authentication, patterns
- [Security Guide](/docs/claude/SECURITY.md) - TR-03187 compliance, authentication, authorization
- [Code Style Guide](/docs/claude/CODE_STYLE.md) - Naming conventions, formatting, best practices
- [Testing Guide](/docs/claude/TESTING.md) - Test organization, running tests, coverage requirements
- [CI/CD Guide](/docs/claude/CICD.md) - Pipeline stages, security, local testing
- [Development Patterns](/docs/claude/DEVELOPMENT_PATTERNS.md) - API design, data access, event-driven, config adapter
