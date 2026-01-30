# Backend Architecture & Development Patterns

## Six-Layer Architecture

1. **API Layer** - Controllers handling HTTP requests and standardized responses
2. **Response Layer** - Assemblers shaping client-facing output structures
3. **Business Layer** - Services enforcing business rules and coordinating workflows
4. **Conversion Layer** - Mappers transforming data between internal/external formats
5. **Data Access Layer** - Repositories providing structured database access
6. **Domain Model** - Entities, DTOs, and specifications defining core structures

**Layer Dependencies**: Strict downward flow only (API → Business → Data → Domain), preventing circular coupling.

## Six-Layer Implementation Flow (for new entities)

1. **Model** (Entity, DTOs, Specifications)
2. **Data access** (Repository, Specification wiring)
3. **Mapping** (MapStruct Mapper)
4. **Business logic** (Service + lifecycle hooks)
5. **Response construction** (Assembler)
6. **API exposure** (Controller)

## Key Packages (under `de.civitascore.portal`)

- `configuration/` - Spring configuration (Security, WebMvc, Auditor)
- `controller/` - REST controllers (extend `BaseController`, thin delegation to services)
- `model/entity/` - JPA entities with base classes (in `portal-model` module, shared with AuthZ)
- `model/embedded/` - Enum types for entities (in `portal-model` module)
- `model/input/` - Request DTOs with relations as IDs
- `model/output/` - Response DTOs with nested summaries (full responses)
- `repository/` - JPA repositories with EntityGraph support
- `repository/specification/` - Query specifications for filtering
- `mapper/` - MapStruct mappers (`toEntity`, `toOutput`, `toInput`, `updateEntity`)
- `security/` - OAuth2 security configuration and DTOs
- `util/` - Utility classes

## Entity Creation

- Extend `BaseEntity` (id, audit fields), `NamedEntity` (adds name, description), or `ScopedEntity` (adds scope fields)
- Use inheritance hierarchy from `model/entity/base/`

## DTOs

- **Input DTOs**: Represent client data with relations as IDs
- **Output DTOs**: Full responses with nested summaries
- **Summary DTOs**: Minimal representations for nested relations
- Use MapStruct for mapping (annotate with `@Mapper(componentModel = "spring")`)
- Implement: `toEntity`, `toOutput`, `toInput`, `updateEntity` methods
- Ignore relation fields in mappers (handle at service layer)

## Controllers

- Extend `BaseController` with generic type parameters
- Use `@RestController`, serve as thin REST adapters
- Delegate all logic to Services and Assemblers
- Return ResponseEntity with HATEOAS links

## Services

- Extend `BaseService` to orchestrate repositories and domain rules
- Provide lifecycle hooks: input processing, entity conversion, persistence, queries, deletion
- Relation handling always resides at service layer
- Transaction boundaries managed automatically

## Assemblers

- Handle response shaping through template flow: pre-processing → base field mapping → enrichment → post-processing
- Isolate response logic from services and controllers

## Repositories

- Build on Spring Data JPA with Specifications support
- Define `EntityGraph` queries for eager loading when needed
- Provide CRUD operations, filtering, pagination, query optimization

## Specifications

Create query specifications in `repository/specification/` for declarative filtering via request parameters.

## Database Migrations

Add Flyway migrations as `V{number}__{description}.sql` in `src/main/resources/db/migration/`.

**CRITICAL**: Never modify existing migrations; always create new ones.

## OpenAPI/API Design

- All endpoints must have OpenAPI specification defined from the start
- OpenAPI serves as the source of truth for API contracts
- Follow RESTful conventions (plural nouns, proper HTTP methods)
- Generate documentation automatically from specifications
