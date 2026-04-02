# CIVITAS CORE Portal Model

Shared JPA entity library used by [portal-backend](../portal-backend/) and [config-adapter](../config-adapter/).

## Contents

**Entities** (`de.civitascore.portal.model.entity`):
DataSet, DataSource, DataSpace, DataStructure, DataStructureVersion, Distribution, Pipeline, Catalog, DataSetSeries, Resource, Agent, Activity, User, Group, Role, Permission, Assignment

**Base classes** (`entity.base`):
BaseEntity, NamedEntity, BaseDataEntity, AssignableEntity

**Enums** (`de.civitascore.portal.model.embedded`):
DataSetStatus, DataSourceStatus, DataStructureStatus, DataStructureVersionStatus, ConnectorType, PipelineAction, ScopeType, RoleType, RoleDefault, PendingSagaType, SagaResultType, PermissionName, PermissionType, PermissionCategory, PermissionSource, UserTitleType, DataStructureVersionSource

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
