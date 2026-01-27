# Development Patterns & Best Practices

## API Design & Integration

### RESTful Conventions
- Use HATEOAS links in API responses for resource navigation
- Implement consistent error handling across all endpoints
- Follow OpenAPI 3 specification for API documentation
- Return appropriate HTTP status codes and error payloads

### Service Integration
- Prefer API Management layer over direct service-to-service calls
- Use event-driven patterns (Kafka/CloudEvents) for async operations
- Implement circuit breakers and retries for external service calls
- Design APIs with backward compatibility in mind

### Data Access Patterns
- Implement query specifications for complex filtering (backend)
- Use JPA/Hibernate with proper lazy/eager loading configuration
- Leverage database migrations (Flyway) for schema evolution
- Design for read-heavy workloads with appropriate caching strategies

### Cross-Component Communication
- Frontend proxies to backend via API routes (`/api/*`)
- Config adapter consumes events from Kafka and publishes results
- All components authenticate users via Keycloak OAuth2
- Use correlation IDs for tracing requests across services

## Code Quality

- Always run formatters before committing: `spotless:apply` (Java) or `pnpm format` (frontend)
- Google Java Format plugin enforces all Java formatting rules (indentation, braces, line wrapping, imports)
- Use pre-commit hooks for consistent code formatting
- Follow idempotent design patterns for all operations
- Maintain test coverage for new features and bug fixes
- Enforce formatting and OpenAPI validation in CI pipeline

## Configuration Management

- Prefer configuration over hardcoding for policy changes
- Use environment variables for deployment-specific settings (UPPERCASE_UNDERSCORE naming)
- Keep secrets out of version control (use `.env.local` templates)
- Support conditional behavior through configuration toggles
- Default security mechanisms to enabled state (secure by default)

## Resource Management

- Design for independent scaling and parallel execution (cloud-native principle)
- Use proper resource cleanup patterns (try-with-resources for Java, context managers for Python)
- Implement health check endpoints for Kubernetes probes (readiness/liveness)
- Handle service startup delays with retries rather than hard failures
- Design for horizontal scalability (containerized deployment)

## Event-Driven Patterns

- Follow CloudEvents specification for event structure (ADR 013)
- Use correlation tracking for event chains
- Implement idempotent event handlers
- Design for eventual consistency in distributed operations
- Kafka for async communication between components

## Config Adapter Development

### Creating Adapters
1. Extend `AbstractConfigAdapter`
2. Implement `getName()`, `getSubscribedTopics()`, `processConfigEvent()`
3. Register via ServiceLoader in `META-INF/services/com.civitas.configadapter.adapter.ConfigAdapter`
4. Add configuration properties (adapter name prefix)

### Event Handling
Events follow CloudEvents spec. Topics defined in `Topics` class.

### Configuration
Use `AdapterConfig` interface for property access. Application uses `ApplicationConfig` for app-level config.

### Testing
Adapters only need `config-adapter-configuration` in test scope. Use `AppConfig` for tests.

## Development Principles

- **Modularity**: Keep core functionality minimal; domain components must be interchangeable
- **Standard Solutions Priority**: Evaluate existing components before custom development
- **Technology Consistency**: Use stable, long-lived technologies; minimize diversity
- **Well-Defined Interfaces**: Expose capabilities through clear, documented standard interfaces
- **Open Source First**: All operational artifacts must be published
