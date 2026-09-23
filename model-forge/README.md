# CIVITAS/CORE Model Forge

> Embedded Java runtime for data modelling, versioning and integration
> configuration within the
> [CIVITAS/CORE](https://gitlab.com/civitas-connect/civitas-core) platform.

[![Pipeline](https://gitlab.com/kernblick_oss/civitas-core/model-forge/badges/main/pipeline.svg)](https://gitlab.com/kernblick_oss/civitas-core/model-forge/-/pipelines)
[![Coverage](https://gitlab.com/kernblick_oss/civitas-core/model-forge/badges/main/coverage.svg)](https://gitlab.com/kernblick_oss/civitas-core/model-forge/-/pipelines)
[![Release](https://gitlab.com/kernblick_oss/civitas-core/model-forge/-/badges/release.svg)](https://gitlab.com/kernblick_oss/civitas-core/model-forge/-/releases)
[![License: EUPL-1.2](https://img.shields.io/badge/License-EUPL_1.2-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![JSON Schema 2020-12](https://img.shields.io/badge/JSON_Schema-2020--12-blue.svg)](https://json-schema.org/draft/2020-12)
[![SBOM: CycloneDX](https://img.shields.io/badge/SBOM-CycloneDX-4DB6AC.svg)](https://gitlab.com/kernblick_oss/civitas-core/model-forge/-/dependencies)

Model Forge is consumed as Java/Spring Boot modules by a host application —
`portal-backend` in `civitas-core-platform` is the host integrating it. It is not a standalone
service.

The stable integration boundary is the embedded Java facade.

## Modules

| Module | Purpose |
|--------|---------|
| `model-forge-contract` | Public records (commands, results, queries), the `ModelForge` facade interface and URN helpers. No Spring, HTTP, JDBC, Flyway or PostgreSQL dependencies. |
| `model-forge-runtime` | The embedded runtime: facade implementation, ports, use cases, PostgreSQL registry adapter (Flyway migrations) and HTTP integration adapters. Internal slice boundaries are enforced by ArchUnit tests. |
| `model-forge-spring-boot-starter` | Spring Boot 4 auto-configuration for host applications. |
| `model-forge-admin-ui` | Local Wicket-based developer/debug UI. A consumer of the library through the public facade, not part of the embedded runtime. |

## Spring Boot Usage

Add the starter to the host application:

```xml
<dependency>
  <groupId>de.civitascore</groupId>
  <artifactId>core-model-forge-spring-boot-starter</artifactId>
  <version>${model-forge.version}</version>
</dependency>
```

## Versioning

Model Forge uses the `${revision}` scheme of the other mono-repo Maven modules
(`portal-model`, `config-adapter`). `model-forge/pom.xml` holds the base version, and CI
publishes it under a branch-dependent coordinate:

| Pipeline | Published version |
|---|---|
| `main`, tags | `0.1.0` |
| `develop` | `0.1.0-SNAPSHOT` |
| merge request | `0.1.0-MR-<iid>-SNAPSHOT` |

A repository-wide single version was rejected: `portal-backend` is Model Forge's only consumer,
so a shared version buys no cross-module traceability while forcing every unrelated module to
bump on each release.

`portal-backend` is the only consumer. Its build resolves the version this pipeline
published (`-Dmodel-forge.version`), so it always compiles against the Model Forge on its
own branch instead of whatever last wrote a shared coordinate; two merge requests
touching Model Forge no longer overwrite each other's artifact.

`portal-backend`'s `model-forge.version` property falls back to `0.1.0-SNAPSHOT`
(develop's line) for builds that pass no version — a fresh clone, or CI's dependency-graph
job. The dev-environment scripts install Model Forge under their own `-Drevision` and hand
the backend the same value, so a locally changed module reaches the backend build.

Model Forge reuses the host application's `spring.datasource`. For the
`portal-backend` integration this matches the existing PostgreSQL setup:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/portal_backend?sslmode=require
    driver-class-name: org.postgresql.Driver

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    properties:
      hibernate:
        jdbc:
          lob.non_contextual_creation: true
```

Model Forge itself stays JDBC/Flyway based. It does not register JPA entities,
web controllers, API-key filters or MVC security. The starter configures a
separate Flyway migration for the Model-Forge schema:

```yaml
model-forge:
  registry:
    schema: model_forge
    migration-table: model_forge_schema_history
```

Inject the facade in host services:

```java
import de.civitascore.modelforge.facade.ModelForge;
import org.springframework.stereotype.Service;

@Service
class ImportWorkflow {
    private final ModelForge modelForge;

    ImportWorkflow(ModelForge modelForge) {
        this.modelForge = modelForge;
    }
}
```

The host application is responsible for its own external API, authentication
and authorization. Model Forge provides the embedded domain capability.

## Data Ownership

Host and Model Forge share one PostgreSQL database but own separate schemas:

- The host owns its own tables (administration, lifecycle, authorization) in
  its schema, migrated by its own Flyway setup.
- Model Forge owns the model content — schema documents, their versions,
  formats and reference graph — in the `model_forge` schema, migrated by the
  starter's dedicated Flyway instance.

The host references artifacts exclusively by their **versioned CORE URN**
stored in a plain text column — never by a foreign key across the schema
boundary, and never by reading `model_forge.*` tables directly. The only
access path to model content is the `ModelForge` facade; consistency comes
from the shared datasource and transaction manager (one commit covers the
host write and the registry write).

For the same reason the host keeps no copy of the reference graph in tables of
its own. It asks the registry which artifacts depend on a given one, rather
than maintaining a second record of the same edges — a copy would have to be
kept in step with the original, and the two would answer differently the moment
they drifted.

## Deletion

A released model is not deleted. It is taken back to draft and replaced by a
newer version; deleting is what clears a draft or a mistake.

Five rules decide what one delete removes.

1. Deleting a Data Structure removes the Elements that nothing else uses.
   Deleting a Data Source removes nothing besides itself.
2. Deleting a Pipeline also removes the Mappings no other Pipeline uses.
3. Deleting a Data Set also removes its Pipelines and its Mappings.
4. A delete that removes several artifacts removes all of them or none.
5. A delete happens only while nothing uses the artifact.

Rules 1 to 3 follow from **ownership**, which the registry reads off the
reference itself rather than from everything an artifact points at. A grouping
owns the Elements it is built from, a Pipeline owns the Mapping it wires, and a
Data Set owns its Pipelines and its Mappings — the artifacts reached only
through it. Every other reference names something used rather than owned: the
two Data Structures a Mapping joins exist independently of it, and a Data
Structure, a Data Source and a Data Sink each have an address of their own, so
a Data Set groups those and they outlive it. An owned artifact is removed only
once the container is gone and nothing else holds it, so a shared Element
survives, and so does a Mapping a second Pipeline uses.

Under rule 5 any reference from another artifact refuses the delete, and the
refusal names what is in the way so the reference can be removed where it is
held. Data Set membership is the exception: it is counted rather than refusing
on its own, so an artifact belonging to one Data Set is deleted and unlinked
from it, while one belonging to two is refused until it is removed from the
others.

Only an artifact's **current** version is asked. A reference recorded by a
superseded version states what that version declared and stays readable, but it
holds nothing back: a Data Sink repointed at a newer Data Structure leaves the
older one free.

An artifact is removed with its whole history; versions are not deleted one at
a time.

## Local Build

There is no root `pom.xml` in this repository — run Maven from `model-forge/`:

```bash
mvn test
mvn verify
```

The TypeScript/Zod CORE types are generated in the frontend directly from these runtime JSON
Schemas (the single source of truth) — there is no separate npm package:

```powershell
cd ../portal-frontend
npm run generate:core-types
```

## Monorepo Move Set

The directories intended to move 1:1 into `civitas-core-platform` are:

- `model-forge-contract`
- `model-forge-runtime`
- `model-forge-spring-boot-starter`

`model-forge-admin-ui` is intentionally not part of the move set. It is a local
developer/debug UI that consumes the library through the public facade, like
any other host application.

Schema and use-case examples are kept under `model-forge/schemas/`. They are
not part of the embedded runtime move set.

The remaining root-level build/release scaffolding should be replaced by the
monorepo's own build, CI and documentation structure.

## Architecture Rules

The embedded boundary is enforced by module structure and architecture tests:

- Contract has no Spring, web, JDBC, Flyway, PostgreSQL, Servlet, OpenAPI or
  HAL dependencies.
- Core and application code depend on ports, not PostgreSQL adapters.
- The Spring Boot starter registers facade and infrastructure beans, but no
  controllers or web-security configuration.

## License

Licensed under the [European Union Public Licence v. 1.2 (EUPL-1.2)](LICENSE).

Copyright (c) 2025-2026 Civitas Connect e.V.
