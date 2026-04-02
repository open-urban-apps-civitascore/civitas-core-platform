# CIVITAS CORE Portal Model

Shared JPA entity library used by [portal-backend](../portal-backend/) and [config-adapter](../config-adapter/).

## Contents

| Package | Description |
|---------|-------------|
| `de.civitascore.portal.model.entity` | JPA entities (DataSet, DataSource, Pipeline, etc.) |
| `de.civitascore.portal.model.entity.base` | Abstract base classes (BaseEntity, AssignableEntity, etc.) |
| `de.civitascore.portal.model.embedded` | Enums for status, roles, permissions, and saga types |

## Build

```bash
mvn clean install
mvn spotless:apply   # Format (Google Java Format)
```

## Usage

Add as a Maven dependency:

```xml
<dependency>
    <groupId>de.civitascore</groupId>
    <artifactId>portal-model</artifactId>
    <version>${portal-model.version}</version>
</dependency>
```
