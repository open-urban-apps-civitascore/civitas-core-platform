# core-model-forge-contract

Shared Java contract model for the
[CIVITAS/CORE Model Forge](https://gitlab.com/kernblick_oss/civitas-core/model-forge).

This module is not a generated HTTP client. It is a small plain-Java jar with
records and utility classes that form part of the embedded Model Forge public
contract.

## What Is In This Module

| Package | Contents |
|---------|----------|
| `de.civitascore.modelforge.domain` | CORE domain records: `DataSetDto`, `ElementDto`, `DataSourceDto`, `DataSinkDto`, `MappingDto`, `PipelineDto`, `DataStructureDto` and the generic `Envelope<T>` wrapper |
| `de.civitascore.modelforge.dto` | Internal diagnostic result records shared by the validation and application layers: `DiagnosticDto`, `ValidationResultDto` |
| `de.civitascore.modelforge.urn` | `UrnParser`, a stateless helper for parsing CORE URNs |

The jar intentionally does not contain controllers, services, persistence code,
Spring Boot configuration, an HTTP transport client or TypeScript types.

## Requirements

- Java 25
- Maven, when building from this repository

Runtime dependencies for consumers are limited to:

- `com.fasterxml.jackson.core:jackson-databind`

No Spring Boot runtime is required by this module.

## Local Build

From the repository root:

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.contract.ImportSchemaCommand;

ObjectMapper objectMapper = new ObjectMapper();
JsonNode schema = objectMapper.readTree("""
    {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "$id": "urn:core:platform:civitas:element:common:GeoPoint:1.0.0",
      "title": "GeoPoint",
      "type": "object"
    }
    """);

var command = new ImportSchemaCommand(schema, "default");
```

Use the shared domain records:

```java
import de.civitascore.modelforge.domain.DataSetDto;
import java.util.List;

var dataSet = new DataSetDto(
    "urn:core:platform:civitas:dataset:common:Default:1.0.0",
    "Default",
    null,
    "1.0.0",
    List.of(),
    List.of(),
    List.of(),
    List.of(),
    List.of()
);
```

Parse CORE URNs without Spring:

```java
import de.civitascore.modelforge.urn.UrnParser;

String urn = "urn:core:platform:civitas:element:common:GeoPoint:1.0.0";

String name = UrnParser.nameFromUrn(urn);       // "GeoPoint"
String version = UrnParser.versionFromUrn(urn); // "1.0.0"
String logical = UrnParser.logicalUrn(urn);     // "urn:core:platform:civitas:element:common:GeoPoint"
```

## Versioning

The repository uses semantic-release for release management (see
[`.releaserc.json`](../.releaserc.json) and the CI/CD docs). Until a release tag
is published, consumers should use the current snapshot version:
`0.1.0-SNAPSHOT`.

Breaking changes to public records or utility method behavior require a major
version bump once the project leaves the initial `0.x` phase.

## License

Licensed under the [European Union Public Licence v. 1.2 (EUPL-1.2)](../LICENSE).

Copyright (c) 2025-2026 Civitas Connect e.V.
