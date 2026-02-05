# Dataset-Centric Saga Pattern Implementation Plan

## Overview

Refactor the config-adapter to support a choreography-based saga pattern for dataset lifecycle management. Creating a dataset triggers creation of a FROST Project and APISIX Route in sequence, with each step publishing events for the next.

## Event Flow

### CREATE Flow
```
Portal Backend                 FrostAdapter                    ApisixAdapter
     |                              |                               |
     |--[dataset.created]---------->|                               |
     |                        Create FROST Project                  |
     |                        (with dataset_reference property)     |
     |                              |--[dataset.createdInSTA]------>|
     |                              |                        Create APISIX Route
     |                              |                        (configurable template)
     |<------------------------------------------[dataset.apiRouteCreated]
```

### DELETE Flow (Reverse Order)
```
Portal Backend                 ApisixAdapter                   FrostAdapter
     |                              |                               |
     |--[dataset.deleted]---------->|                               |
     |                        Delete APISIX Route                   |
     |                              |--[dataset.deletedFromAPI]---->|
     |                              |                        Delete FROST Project
     |<-----------------------------------------[dataset.deletedFromSTA]
```

## Design Decisions

1. **Delete Order**: Reverse order - APISIX deletes route first, then FROST deletes project
2. **Route Config**: Configurable template via properties (`apisix.dataset.route.uri.template`, `apisix.dataset.route.upstream.id`)
3. **Partial Failure**: Mark as partial with `result.status='error'`, leave created resources, Portal handles cleanup/retry
4. **RedpandaConnect**: Not included in this phase

## Files to Create

### 1. DatasetConfigValue
**Path**: `config-adapter-api/src/main/java/com/civitas/configadapter/model/dataset/DatasetConfigValue.java`

Flexible map-based ConfigValue for dataset payloads (similar pattern to FrostConfigValue):
- Core fields: `id`, `name`, `description`, `created_by`, `modified_by`, `external_id`, `format`
- Saga enrichment fields: `sta_project_id`, `api_route_id`
- Future fields: `datasources`, `datapipelines`
- Status tracking: `result` object with `status` and `message`
- Method `withStaProjectId(String)` and `withApiRouteId(String)` for immutable enrichment
- Method `withResult(DatasetResult)` for status updates

### 2. DatasetResult
**Path**: `config-adapter-api/src/main/java/com/civitas/configadapter/model/dataset/DatasetResult.java`

Record for saga status:
```java
public record DatasetResult(String status, String message) {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_ERROR = "error";
}
```

### 3. Unit Tests
- `config-adapter-api/src/test/java/com/civitas/configadapter/model/dataset/DatasetConfigValueTest.java`
- `config-adapter-frost/src/test/java/com/civitas/configadapter/frost/FrostAdapterDatasetTest.java`
- `config-adapter-apisix/src/test/java/com/civitas/configadapter/apisix/ApisixAdapterDatasetTest.java`

## Files to Modify

### 1. Topics.java
**Path**: `config-adapter-api/src/main/java/com/civitas/configadapter/Topics.java`

Add new dataset saga topics:
```java
// Dataset Management Saga Events - Initial events from Portal
DATASET_CREATED("core.civitas.datamanagement.dataset.created"),
DATASET_UPDATED("core.civitas.datamanagement.dataset.updated"),
DATASET_DELETED("core.civitas.datamanagement.dataset.deleted"),

// Saga progression - FROST to APISIX
DATASET_CREATED_IN_STA("core.civitas.datamanagement.dataset.createdInSTA"),
DATASET_UPDATED_IN_STA("core.civitas.datamanagement.dataset.updatedInSTA"),

// Saga progression - APISIX to FROST (for delete)
DATASET_DELETED_FROM_API("core.civitas.datamanagement.dataset.deletedFromAPI"),

// Saga completion events - back to Portal
DATASET_API_ROUTE_CREATED("core.civitas.datamanagement.dataset.apiRouteCreated"),
DATASET_API_ROUTE_UPDATED("core.civitas.datamanagement.dataset.apiRouteUpdated"),
DATASET_DELETED_FROM_STA("core.civitas.datamanagement.dataset.deletedFromSTA"),
```

### 2. ConfigValue.java
**Path**: `config-adapter-api/src/main/java/com/civitas/configadapter/model/ConfigValue.java`

Add new subtype registration:
```java
@JsonSubTypes.Type(value = DatasetConfigValue.class, name = "dataset"),
```

### 3. FrostAdapter.java
**Path**: `config-adapter-frost/src/main/java/com/civitas/configadapter/frost/FrostAdapter.java`

Add dataset event handling:

1. Add `isDatasetTopic(String topic)` method to detect dataset events
2. Add `processDatasetEvent(String topic, ConfigEvent event)` router method
3. Add `handleDatasetCreate(DatasetConfigValue dataset, ConfigEvent event)`:
   - Build Project payload with `name`, `description`, `properties.dataset_reference`
   - POST to FROST `/Projects`
   - Extract `sta_project_id` from Location header
   - Create enriched dataset with `sta_project_id` and `result.status="ok"`
   - Publish ConfigEvent to `DATASET_CREATED_IN_STA` topic
4. Add `handleDatasetUpdate(DatasetConfigValue dataset, ConfigEvent event)`:
   - PATCH FROST Project using `sta_project_id` from payload
   - Publish to `DATASET_UPDATED_IN_STA` topic
5. Add `handleDatasetDelete(DatasetConfigValue dataset, ConfigEvent event)`:
   - Triggered by `DATASET_DELETED_FROM_API` topic (after APISIX deletes route)
   - DELETE FROST Project using `sta_project_id` from payload
   - Publish to `DATASET_DELETED_FROM_STA` topic (saga completion)
6. Add `publishDatasetSagaEvent(String topic, DatasetConfigValue enrichedDataset, ConfigEvent originalEvent)` helper

### 4. ApisixAdapter.java
**Path**: `config-adapter-apisix/src/main/java/com/civitas/configadapter/apisix/ApisixAdapter.java`

Add dataset saga event handling:

1. Add `isDatasetSagaTopic(String topic)` to detect dataset saga events
2. Add `processDatasetSagaEvent(String topic, ConfigEvent event)` router method
3. Add `handleDatasetRouteCreate(DatasetConfigValue dataset, ConfigEvent event)`:
   - Build route config from configurable template:
     - URI: `apisix.dataset.route.uri.template` (default: `/api/v1/frost/projects/{sta_project_id}/*`)
     - Upstream ID: `apisix.dataset.route.upstream.id` (default: `frost-server`)
     - Route ID: derived from dataset ID for idempotency
   - POST to APISIX `/apisix/admin/routes`
   - Extract `api_route_id` from response
   - Create enriched dataset with `api_route_id` and `result.status="ok"`
   - Publish ConfigEvent to `DATASET_API_ROUTE_CREATED` topic
4. Add `handleDatasetRouteUpdate(DatasetConfigValue dataset, ConfigEvent event)`:
   - PUT to APISIX using `api_route_id` from payload
   - Publish to `DATASET_API_ROUTE_UPDATED` topic
5. Add `handleDatasetRouteDelete(DatasetConfigValue dataset, ConfigEvent event)`:
   - Triggered by `DATASET_DELETED` topic (first step of delete saga)
   - DELETE APISIX route using `api_route_id` from payload
   - Publish to `DATASET_DELETED_FROM_API` topic (triggers FROST deletion)
6. Add `buildRouteConfigFromDataset(DatasetConfigValue dataset)` helper
7. Add `publishDatasetSagaEvent(String topic, DatasetConfigValue enrichedDataset, ConfigEvent originalEvent)` helper

### 5. Configuration Updates

Add new properties to application configuration:

```properties
# FROST adapter - add dataset topics
frost.topics=...,core.civitas.datamanagement.dataset.created,core.civitas.datamanagement.dataset.updated,core.civitas.datamanagement.dataset.deletedFromAPI

# APISIX adapter - add dataset saga topics
apisix.topics=...,core.civitas.datamanagement.dataset.createdInSTA,core.civitas.datamanagement.dataset.updatedInSTA,core.civitas.datamanagement.dataset.deleted

# Dataset route configuration
apisix.dataset.route.uri.template=/api/v1/frost/projects/{sta_project_id}/*
apisix.dataset.route.upstream.id=frost-server
apisix.dataset.route.plugins.enabled=prometheus,proxy-rewrite
```

## Implementation Sequence

### Phase 1: Core Model
1. Create `DatasetResult` record
2. Create `DatasetConfigValue` class with Jackson annotations
3. Add dataset topics to `Topics.java`
4. Register `DatasetConfigValue` in `ConfigValue.java`
5. Write `DatasetConfigValueTest`

### Phase 2: FrostAdapter Changes
1. Add dataset topic detection and routing in `doProcessConfigEvent`
2. Implement `handleDatasetCreate` with Project creation and saga event publishing
3. Implement `handleDatasetUpdate` and `handleDatasetDelete`
4. Write `FrostAdapterDatasetTest`

### Phase 3: ApisixAdapter Changes
1. Add dataset saga topic detection and routing
2. Implement route configuration builder from dataset
3. Implement `handleDatasetRouteCreate` with saga event publishing
4. Implement `handleDatasetRouteUpdate` and `handleDatasetRouteDelete`
5. Write `ApisixAdapterDatasetTest`

### Phase 4: Integration Testing
1. Update application configuration for both adapters
2. Write integration tests with Testcontainers (Kafka + FROST + APISIX)
3. Test full saga flows: create, update, delete
4. Test partial failure scenarios

## Error Handling

- **Network errors (5xx)**: `RetryableAdapterException` - Kafka handler retries with exponential backoff
- **Client errors (4xx)**: `FatalAdapterException` - set `result.status="error"` in dataset, publish error event, send to DLQ
- **Partial saga failure**: Enriched dataset with `result.status="error"` and `result.message` explaining failure point; Portal Backend handles cleanup/retry decisions

## Verification

1. Run unit tests: `mvn clean verify -pl config-adapter-api,config-adapter-frost,config-adapter-apisix`
2. Run integration tests with Testcontainers
3. Manual verification with local Kafka, FROST, and APISIX instances:
   - Send `dataset.created` event to Kafka
   - Verify FROST Project created with `dataset_reference` property
   - Verify `dataset.createdInSTA` event published with `sta_project_id`
   - Verify APISIX route created with correct URI pattern
   - Verify `dataset.apiRouteCreated` event published with `api_route_id`
   - Test delete flow in reverse order

## Backward Compatibility

- All existing entity-level topics (`core.civitas.data.thing.created`, etc.) continue to work unchanged
- Existing `FrostConfigValue` and `RouteConfigValue` used for direct entity operations
- Dataset operations use separate code paths with dedicated `DatasetConfigValue`
