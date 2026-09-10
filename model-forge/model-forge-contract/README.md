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
The current source version is `0.1.0-SNAPSHOT`.

## Maven Usage

The CI pipeline publishes Maven artifacts to the GitLab Package Registry on
pushes to `main`. For tags matching `v<major>.<minor>.<patch>`, the pipeline
sets the Maven version to the tag without the leading `v` before publishing.

```xml
<repositories>
  <repository>
    <id>gitlab-model-forge</id>
    <url>https://gitlab.com/api/v4/projects/YOUR_PROJECT_ID/packages/maven</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>de.civitascore</groupId>
    <artifactId>core-model-forge-contract</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Replace `YOUR_PROJECT_ID` with the numeric GitLab project ID.

For private package registry access, add matching credentials to
`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>gitlab-model-forge</id>
      <username>DEPLOY_TOKEN_USERNAME</username>
      <password>DEPLOY_TOKEN_OR_ACCESS_TOKEN</password>
    </server>
  </servers>
</settings>
```

## Gradle Usage

```kotlin
repositories {
    maven {
        url = uri("https://gitlab.com/api/v4/projects/YOUR_PROJECT_ID/packages/maven")
        credentials(HttpHeaderCredentials::class) {
            name = "Private-Token"
            value = System.getenv("GITLAB_TOKEN")
        }
        authentication {
            create<HttpHeaderAuthentication>("header")
        }
    }
}

dependencies {
    implementation("de.civitascore:core-model-forge-contract:0.1.0-SNAPSHOT")
}
```

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
Until a release tag is published, consumers should use the current snapshot
version: `0.1.0-SNAPSHOT`.

Breaking changes to public records or utility method behavior require a major
version bump once the project leaves the initial `0.x` phase.

## License

Licensed under the [European Union Public Licence v. 1.2 (EUPL-1.2)](../LICENSE).

Copyright (c) 2025-2026 Civitas Connect e.V.
