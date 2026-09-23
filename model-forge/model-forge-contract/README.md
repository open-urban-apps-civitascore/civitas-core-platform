# core-model-forge-contract

Shared Java contract model for the
[CIVITAS/CORE Model Forge](https://gitlab.com/kernblick_oss/civitas-core/model-forge).

This module is not a generated HTTP client. It is a small plain-Java jar with
records and utility classes that form part of the embedded Model Forge public
contract.

## What Is In This Module

| Package | Contents |
|---------|----------|
| `de.civitascore.modelforge.contract` | Commands, queries, results and exceptions: `CreateArtifactCommand`, `SaveArtifactCommand`, `ImportSchemaCommand`, `ArtifactId`, `ArtifactKind`, `ArtifactView`, `ArtifactWriteResult`, `ValidationResult`, `Diagnostic`, … |
| `de.civitascore.modelforge.facade` | `ModelForge`, the embedded facade interface a host calls |
| `de.civitascore.modelforge.domain` | `Formats`, the shared format and content-type tokens |
| `de.civitascore.modelforge.urn` | `UrnParser`, a stateless helper for parsing CORE URNs |

The jar intentionally does not contain controllers, services, persistence code,
Spring Boot configuration, an HTTP transport client or TypeScript types.

## Requirements

- Java 25
- Maven, when building from this repository

Runtime dependencies for consumers are limited to:

- `tools.jackson.core:jackson-databind` (Jackson 3)

No Spring Boot runtime is required by this module.

## Local Build

There is no root `pom.xml` in this repository — run Maven from `model-forge/`:

```bash
mvn -pl model-forge-contract -am test
```

Install the jar into the local Maven repository:

```bash
mvn -pl model-forge-contract -am install
```

The module inherits dependency management and the version from the parent POM.
The base version is `0.1.0`; CI appends a branch suffix (see
[Versioning](#versioning)).

## Usage

Add as a Maven dependency:

```xml
<dependency>
  <groupId>de.civitascore</groupId>
  <artifactId>core-model-forge-contract</artifactId>
  <version>${model-forge.version}</version>
</dependency>
```

This module publishes to the mono-repo's own GitLab Maven registry, already declared as a
repository in `portal-backend/pom.xml` — the only consumer. There is no separate registry or
credentials setup for this module; see the [reactor README](../README.md#versioning) for how
`${model-forge.version}` is resolved.

## Examples

Build an embedded facade command:

```java
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

ObjectMapper objectMapper = new ObjectMapper();
JsonNode schema = objectMapper.readTree("""
    {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "$id": "urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx:1.0.0",
      "title": "GeoPoint",
      "type": "object"
    }
    """);

var command = new ImportSchemaCommand(schema);
```

Create a new artifact — the caller supplies only a display name; Model Forge mints the URN
and the version and returns the versioned pin in `ArtifactWriteResult.artifactId()`:

```java
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.CreateArtifactCommand;

var command = new CreateArtifactCommand(ArtifactKind.DATA_SET, "Air Quality", content);
```

Take an `ArtifactId` apart, or parse CORE URNs directly, without Spring:

```java
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.urn.UrnParser;

var id = new ArtifactId("urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx:1.0.0");

id.name();       // "GeoPoint"
id.version();    // "1.0.0"
id.logicalUrn(); // "urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx"

String version = UrnParser.versionFromUrn(id.logicalUrn()); // null — logical URNs carry no version
```

A CORE URN has eight colon-separated segments in its logical form
(`urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>`) and nine when a version is
pinned. `versionFromUrn` returns `null` for the logical form.

## Versioning

The mono-repo owns the release process; this module does not use semantic-release.
The reactor's `${revision}` scheme applies: `main`/tags publish `0.1.0`, `develop`
publishes `0.1.0-SNAPSHOT`, and a merge request publishes `0.1.0-MR-<iid>-SNAPSHOT`
(see the [reactor README](../README.md#versioning)).

Breaking changes to public records or utility method behavior require a major
version bump once the project leaves the initial `0.x` phase.

## License

Licensed under the [European Union Public Licence v. 1.2 (EUPL-1.2)](../LICENSE).

Copyright (c) 2025-2026 Civitas Connect e.V.
