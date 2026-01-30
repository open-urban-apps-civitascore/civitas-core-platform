# Testing Guidelines & Best Practices

## Test Types Overview

The platform has the following test suites. **Run all tests for major changes** (new features, refactoring, dependency updates).

| Component | Test Type | Command | Count | Notes |
|-----------|-----------|---------|-------|-------|
| portal-frontend | Unit (Vitest) | `pnpm test` | 199 | Fast, mock-based |
| portal-frontend | E2E (Playwright) | `pnpm test:e2e` | 42 | Requires Keycloak + backend |
| portal-backend | Unit (JUnit) | `mvn test` | - | Surefire plugin |
| portal-backend | Integration | `mvn verify` | - | Testcontainers, Failsafe |
| authz/repository | Unit (JUnit) | `mvn -f authz/repository/pom.xml test` | 10 | Service layer tests |
| authz/repository | Integration | `mvn -f authz/repository/pom.xml verify` | 9 | Full stack with H2 |
| config-adapter | Unit/Integration | `mvn -f config-adapter/pom.xml test` | - | Kafka via Testcontainers |

### Quick Reference: Running All Tests

```bash
# Frontend (from portal-frontend/)
pnpm test                    # Unit tests
pnpm test:e2e               # E2E tests (requires infrastructure)

# Backend (from portal-backend/)
mvn clean verify            # Unit + Integration tests

# AuthZ Repository (from authz/repository/)
mvn clean verify            # Unit + Integration tests

# Config Adapter (from config-adapter/)
mvn clean test              # All modules
```

### E2E Test Infrastructure Requirements

E2E tests require running infrastructure:
```bash
# Start PostgreSQL
cd dev-environment/postgres && docker compose up -d

# Start Keycloak
cd dev-environment/keycloak && docker compose up -d

# Start backend
cd portal-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres
```

---

## Test Design Principles

**Production Safety**: Mark tests as production-safe only if they are non-destructive and idempotent for repeated execution.

**Fail Fast Strategy**: Catch failures as early as possible during deployment rather than at the end of long execution cycles.

**Performance**: Tests should execute quickly to avoid significantly slowing down development workflow. Avoid complex or long-running checks unless necessary.

**Reliability Over Sensitivity**: When in doubt, prefer to wait or retry rather than fail early. Services might need additional time to become ready.

**Parallel Execution**: Design tests with isolated data and resources to enable parallel execution. Use proper resource cleanup (e.g., `contextlib.ExitStack` in Python, try-with-resources in Java).

**Code Reusability**: Leverage templated, modular test components rather than writing repetitive code. Templates encourage reuse and maintainability.

**Flexibility**: All tests should support conditional execution through configuration toggles.

## Test Organization & Standards

### Backend (JUnit)
- Test method naming: `method_condition_expectedResult` (describes behavior)
- Unit tests: `src/test/java/` - mock dependencies, fast execution, Surefire plugin
- Integration tests: `src/testIntegration/java/` - use `@SpringBootTest` with Testcontainers, Failsafe plugin
- Match package structure between main and test code
- Use Testcontainers for database and external service dependencies
- Enforce via CI: Google Java Format compliance, JaCoCo coverage

### Frontend (Vitest/Playwright)
- Unit test files: `*.test.ts` or `*.spec.ts`
- E2E tests: Playwright with Keycloak authentication required
- Test fixtures: `__tests__/fixtures/` or similar
- Coverage reporting with Cobertura format
- Run modes: watch, coverage, E2E UI mode (--headed for debugging)

### Config Adapter
- Module-specific tests with `config-adapter-configuration` in test scope
- Use Testcontainers for Kafka and external dependencies (requires Docker-in-Docker in CI)
- Test adapters in isolation with `AppConfig` for configuration

## Running Single Tests

### Frontend
```bash
# Unit tests
pnpm test -- path/to/test.test.ts          # Specific file
pnpm test -- -t "test name pattern"        # By name pattern

# E2E tests
pnpm test:e2e -- tests/example.spec.ts     # Specific file
pnpm test:e2e -- --grep "test name"        # By name
```

### Backend
```bash
# Unit tests
mvn test -Dtest=ClassName                   # Specific class
mvn test -Dtest=ClassName#methodName        # Specific method

# Integration tests
mvn verify -Dit.test=ClassName              # Specific integration test
```

### Config Adapter
```bash
# Specific module
mvn test -pl config-adapter-keycloak        # Test one module

# Specific test
mvn test -Dtest=ClassName                   # In current module
```

## Testing Coverage Requirements

- **API Tests**: Every internet-facing application requires API reachability tests
- **Health Checks**: All applications must implement health check endpoints for Kubernetes probes
- **Common Failures**: Focus on testing common failure patterns rather than edge cases
- **Configuration Variations**: Account for different platform configurations in test design
- **No Production Data**: Never use production data in testing/staging environments (ORG-7)
