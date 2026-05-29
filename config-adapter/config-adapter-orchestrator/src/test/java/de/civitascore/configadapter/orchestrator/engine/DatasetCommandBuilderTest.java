/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStatus;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DatasetCommandBuilder}. Verifies that adapter-specific execute and
 * compensation payloads are built correctly from the saga context and step definitions.
 */
class DatasetCommandBuilderTest {

  private static final String DATASET_ID = "ds-test-001";
  private static final String SAGA_ID = "saga-test-001";
  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  // ─── FROST Adapter ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("FROST adapter payloads")
  class FrostPayloads {

    private final SagaStepDefinition createProjectStep =
        SagaStepDefinition.mandatory(
            "create-project",
            "frost",
            "CREATE_PROJECT",
            "DELETE_PROJECT",
            "de.civitascore.dataset.frost.execute",
            "de.civitascore.dataset.frost.compensate");

    private final SagaStepDefinition updateProjectStep =
        SagaStepDefinition.mandatory(
            "update-project",
            "frost",
            "UPDATE_PROJECT",
            "RESTORE_PROJECT",
            "de.civitascore.dataset.frost.execute",
            "de.civitascore.dataset.frost.compensate");

    private final SagaStepDefinition deleteProjectStep =
        SagaStepDefinition.mandatory(
            "delete-project",
            "frost",
            "DELETE_PROJECT",
            null,
            "de.civitascore.dataset.frost.execute",
            null);

    @Test
    @DisplayName("CREATE_PROJECT includes datasetName and description")
    void buildExecutePayload_createProject_shouldIncludeNameAndDescription() {
      SagaContext context =
          createContext(
              SagaType.DATASET_CREATE,
              Map.of(
                  "id",
                  DATASET_ID,
                  "name",
                  "Test Dataset",
                  "description",
                  "A test dataset",
                  "openDataAccess",
                  true,
                  "dataPipelines",
                  List.of()));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, createProjectStep);

      assertEquals("CREATE_PROJECT", payload.get("operation"));
      assertEquals(DATASET_ID, payload.get("datasetId"));
      assertEquals("Test Dataset", payload.get("datasetName"));
      assertEquals("A test dataset", payload.get("description"));
      assertEquals(true, payload.get("openDataAccess"));
    }

    @Test
    @DisplayName("CREATE_PROJECT uses empty string for missing description")
    void buildExecutePayload_createProjectNoDescription_shouldDefaultToEmptyString() {
      SagaContext context =
          createContext(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test Dataset", "openDataAccess", true));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, createProjectStep);

      assertEquals("", payload.get("description"));
    }

    @Test
    @DisplayName("UPDATE_PROJECT includes projectId from properties")
    void buildExecutePayload_updateProject_shouldIncludeProjectIdFromProperties() {
      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "Updated Name",
              "description",
              "Updated desc",
              "openDataAccess",
              true,
              "properties",
              List.of(Map.of("projectId", "proj-123")));

      SagaContext context = createContext(SagaType.DATASET_UPDATE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, updateProjectStep);

      assertEquals("UPDATE_PROJECT", payload.get("operation"));
      assertEquals(DATASET_ID, payload.get("datasetId"));
      assertEquals("proj-123", payload.get("projectId"));
      assertEquals("Updated Name", payload.get("datasetName"));
      assertEquals(true, payload.get("openDataAccess"));
    }

    @Test
    @DisplayName("DELETE_PROJECT includes projectId from properties")
    void buildExecutePayload_deleteProject_shouldIncludeProjectId() {
      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "To Delete",
              "properties",
              List.of(Map.of("projectId", "proj-456")));

      SagaContext context = createContext(SagaType.DATASET_DELETE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, deleteProjectStep);

      assertEquals("DELETE_PROJECT", payload.get("operation"));
      assertEquals("proj-456", payload.get("projectId"));
    }

    @Test
    @DisplayName("FROST compensation includes projectId from step result")
    void buildCompensatePayload_frost_shouldIncludeProjectIdFromResult() {
      SagaStep completedStep =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asSucceeded(
                  Map.of("projectId", "proj-789", "baseUrl", "http://frost/proj-789"),
                  Map.of("projectId", "proj-789"),
                  NOW);

      SagaContext context = createContextWithStep(SagaType.DATASET_CREATE, completedStep);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, completedStep, createProjectStep);

      assertEquals("DELETE_PROJECT", payload.get("operation"));
      assertEquals("proj-789", payload.get("projectId"));
    }

    @Test
    @DisplayName("FROST compensation handles null result gracefully")
    void buildCompensatePayload_frostNullResult_shouldNotFail() {
      SagaStep step = SagaStep.pending("create-project", "frost", "CREATE_PROJECT");
      SagaContext context = createContextWithStep(SagaType.DATASET_CREATE, step);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, step, createProjectStep);

      assertEquals("DELETE_PROJECT", payload.get("operation"));
      assertNull(payload.get("projectId"));
    }
  }

  // ─── APISIX Adapter ─────────────────────────────────────────────────────────

  @Nested
  @DisplayName("APISIX adapter payloads")
  class ApisixPayloads {

    private final SagaStepDefinition createRouteStep =
        SagaStepDefinition.mandatory(
            "create-route",
            "apisix",
            "CREATE_ROUTE",
            "DELETE_ROUTE",
            "de.civitascore.dataset.apisix.execute",
            "de.civitascore.dataset.apisix.compensate");

    private final SagaStepDefinition updateRouteStep =
        SagaStepDefinition.mandatory(
            "update-route",
            "apisix",
            "UPDATE_ROUTE",
            "RESTORE_ROUTE",
            "de.civitascore.dataset.apisix.execute",
            "de.civitascore.dataset.apisix.compensate");

    private final SagaStepDefinition deleteRouteStep =
        SagaStepDefinition.mandatory(
            "delete-route",
            "apisix",
            "DELETE_ROUTE",
            null,
            "de.civitascore.dataset.apisix.execute",
            null);

    @Test
    @DisplayName("CREATE_ROUTE includes upstreamUrl from FROST result and openDataAccess")
    void buildExecutePayload_createRoute_shouldIncludeUpstreamUrlAndAccess() {
      SagaStep frostStep =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asSucceeded(
                  Map.of("projectId", "proj-1", "baseUrl", "http://frost/proj-1"), Map.of(), NOW);

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test", "openDataAccess", true),
              List.of(frostStep, SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, createRouteStep);

      assertEquals("CREATE_ROUTE", payload.get("operation"));
      assertEquals(DATASET_ID, payload.get("datasetId"));
      assertEquals("http://frost/proj-1", payload.get("upstreamUrl"));
      assertEquals(true, payload.get("openDataAccess"));
    }

    @Test
    @DisplayName("CREATE_ROUTE works without FROST result (no upstreamUrl)")
    void buildExecutePayload_createRouteNoFrostResult_shouldOmitUpstreamUrl() {
      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test", "openDataAccess", false),
              List.of(
                  SagaStep.pending("create-project", "frost", "CREATE_PROJECT"),
                  SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, createRouteStep);

      assertNull(payload.get("upstreamUrl"));
      assertEquals(false, payload.get("openDataAccess"));
    }

    @Test
    @DisplayName("UPDATE_ROUTE includes routeId and serviceId from properties")
    void buildExecutePayload_updateRoute_shouldIncludeRouteAndServiceId() {
      SagaStep frostStep =
          SagaStep.pending("update-project", "frost", "UPDATE_PROJECT")
              .asSucceeded(Map.of("baseUrl", "http://frost/proj-1"), Map.of(), NOW);

      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "Updated",
              "openDataAccess",
              true,
              "properties",
              List.of(Map.of("routeId", "route-1"), Map.of("serviceId", "svc-1")));

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_UPDATE,
              trigger,
              List.of(frostStep, SagaStep.pending("update-route", "apisix", "UPDATE_ROUTE")));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, updateRouteStep);

      assertEquals("UPDATE_ROUTE", payload.get("operation"));
      assertEquals("route-1", payload.get("routeId"));
      assertEquals("svc-1", payload.get("serviceId"));
    }

    @Test
    @DisplayName("DELETE_ROUTE includes routeId and serviceId")
    void buildExecutePayload_deleteRoute_shouldIncludeRouteAndServiceId() {
      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "Delete Me",
              "openDataAccess",
              false,
              "properties",
              List.of(Map.of("routeId", "route-2"), Map.of("serviceId", "svc-2")));

      SagaContext context = createContext(SagaType.DATASET_DELETE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, deleteRouteStep);

      assertEquals("DELETE_ROUTE", payload.get("operation"));
      assertEquals("route-2", payload.get("routeId"));
      assertEquals("svc-2", payload.get("serviceId"));
    }

    @Test
    @DisplayName("APISIX compensation includes routeId and serviceId from step result")
    void buildCompensatePayload_apisix_shouldIncludeRouteAndServiceId() {
      SagaStep completedStep =
          SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")
              .asSucceeded(Map.of("routeId", "r-1", "serviceId", "s-1"), Map.of(), NOW);

      SagaContext context = createContextWithStep(SagaType.DATASET_CREATE, completedStep);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, completedStep, createRouteStep);

      assertEquals("DELETE_ROUTE", payload.get("operation"));
      assertEquals("r-1", payload.get("routeId"));
      assertEquals("s-1", payload.get("serviceId"));
    }

    @Test
    @DisplayName("APISIX compensation merges compensationData")
    void buildCompensatePayload_apisixWithCompData_shouldMergeCompensationData() {
      SagaStep completedStep =
          SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")
              .asSucceeded(Map.of("routeId", "r-1"), Map.of("previousConfig", "old"), NOW);

      SagaContext context = createContextWithStep(SagaType.DATASET_CREATE, completedStep);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, completedStep, createRouteStep);

      assertEquals("old", payload.get("previousConfig"));
    }
  }

  // ─── Redpanda Adapter ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("Redpanda adapter payloads")
  class RedpandaPayloads {

    private final SagaStepDefinition deployPipelinesStep =
        SagaStepDefinition.conditional(
            "deploy-pipelines",
            "redpanda",
            "DEPLOY_PIPELINES",
            "DELETE_PIPELINES",
            "de.civitascore.dataset.redpanda.execute",
            "de.civitascore.dataset.redpanda.compensate");

    private final SagaStepDefinition updatePipelinesStep =
        SagaStepDefinition.conditional(
            "update-pipelines",
            "redpanda",
            "UPDATE_PIPELINES",
            "RESTORE_PIPELINES",
            "de.civitascore.dataset.redpanda.execute",
            "de.civitascore.dataset.redpanda.compensate");

    private final SagaStepDefinition deletePipelinesStep =
        SagaStepDefinition.conditional(
            "delete-pipelines",
            "redpanda",
            "DELETE_PIPELINES",
            null,
            "de.civitascore.dataset.redpanda.execute",
            null);

    @Test
    @DisplayName("DEPLOY_PIPELINES includes targetUrl, datasources and dataPipelines")
    void buildExecutePayload_deployPipelines_shouldIncludeTargetUrlAndPipelines() {
      List<Map<String, Object>> pipelines = List.of(Map.of("id", "pl-1", "action", "ADD"));
      List<Map<String, Object>> datasources = List.of(Map.of("id", "src-1", "type", "mqtt"));

      SagaStep frostStep =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asSucceeded(Map.of("baseUrl", "http://frost/proj-1"), Map.of(), NOW);

      Map<String, Object> trigger =
          Map.of(
              "id", DATASET_ID,
              "name", "Test",
              "dataPipelines", pipelines,
              "datasources", datasources);

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              trigger,
              List.of(
                  frostStep,
                  SagaStep.pending("create-route", "apisix", "CREATE_ROUTE"),
                  SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, deployPipelinesStep);

      assertEquals("DEPLOY_PIPELINES", payload.get("operation"));
      assertEquals(DATASET_ID, payload.get("datasetId"));
      assertEquals("http://frost/proj-1", payload.get("targetUrl"));
      assertEquals(pipelines, payload.get("dataPipelines"));
      assertEquals(datasources, payload.get("datasources"));
    }

    @Test
    @DisplayName("DEPLOY_PIPELINES defaults to empty lists when no pipelines/datasources")
    void buildExecutePayload_deployPipelinesNoData_shouldDefaultToEmptyLists() {
      SagaContext context =
          createContext(SagaType.DATASET_CREATE, Map.of("id", DATASET_ID, "name", "Test"));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, deployPipelinesStep);

      assertEquals(List.of(), payload.get("dataPipelines"));
      assertEquals(List.of(), payload.get("datasources"));
    }

    @Test
    @DisplayName("UPDATE_PIPELINES includes datasources and dataPipelines")
    void buildExecutePayload_updatePipelines_shouldIncludePipelines() {
      List<Map<String, Object>> pipelines = List.of(Map.of("id", "pl-2", "action", "UPDATE"));

      Map<String, Object> trigger =
          Map.of(
              "id", DATASET_ID,
              "name", "Test",
              "dataPipelines", pipelines);

      SagaContext context = createContext(SagaType.DATASET_UPDATE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, updatePipelinesStep);

      assertEquals("UPDATE_PIPELINES", payload.get("operation"));
      assertEquals(pipelines, payload.get("dataPipelines"));
    }

    @Test
    @DisplayName("DELETE_PIPELINES includes pipelineIds from properties")
    void buildExecutePayload_deletePipelines_shouldIncludePipelineIds() {
      List<String> pipelineIds = List.of("pl-1", "pl-2");
      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "Test",
              "properties",
              List.of(Map.of("pipelineIds", pipelineIds)));

      SagaContext context = createContext(SagaType.DATASET_DELETE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, deletePipelinesStep);

      assertEquals("DELETE_PIPELINES", payload.get("operation"));
      assertEquals(pipelineIds, payload.get("pipelineIds"));
    }

    @Test
    @DisplayName("Redpanda compensation includes pipelineIds, targetUrl, and datasources")
    void buildCompensatePayload_redpanda_shouldIncludeAllRequiredFields() {
      SagaStep frostStep =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asSucceeded(Map.of("baseUrl", "http://frost/proj-1"), Map.of(), NOW);

      SagaStep redpandaStep =
          SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")
              .asSucceeded(Map.of("pipelineIds", List.of("pl-1")), Map.of(), NOW);

      List<Map<String, Object>> datasources = List.of(Map.of("id", "src-1"));

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test", "datasources", datasources),
              List.of(
                  frostStep,
                  SagaStep.pending("create-route", "apisix", "CREATE_ROUTE"),
                  redpandaStep));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, redpandaStep, deployPipelinesStep);

      assertEquals("DELETE_PIPELINES", payload.get("operation"));
      assertEquals(DATASET_ID, payload.get("datasetId"));
      assertEquals(List.of("pl-1"), payload.get("pipelineIds"));
      assertEquals("http://frost/proj-1", payload.get("targetUrl"));
      assertEquals(datasources, payload.get("datasources"));
    }

    @Test
    @DisplayName("Redpanda compensation defaults datasources to empty list")
    void buildCompensatePayload_redpandaNoDatasources_shouldDefaultToEmptyList() {
      SagaStep step =
          SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")
              .asSucceeded(Map.of("pipelineIds", List.of("pl-1")), Map.of(), NOW);

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE, Map.of("id", DATASET_ID, "name", "Test"), List.of(step));

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, step, deployPipelinesStep);

      assertEquals(List.of(), payload.get("datasources"));
    }
  }

  // ─── Generic Adapter ────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Generic adapter payloads")
  class GenericPayloads {

    private final SagaStepDefinition unknownStep =
        SagaStepDefinition.mandatory(
            "custom-step",
            "custom-adapter",
            "CUSTOM_OP",
            "UNDO_CUSTOM_OP",
            "de.civitascore.dataset.custom.execute",
            "de.civitascore.dataset.custom.compensate");

    @Test
    @DisplayName("Generic execute payload includes all trigger fields and previous results")
    void buildExecutePayload_unknownAdapter_shouldIncludeTriggerAndResults() {
      SagaStep completedStep =
          SagaStep.pending("prev-step", "other", "PREV_OP")
              .asSucceeded(Map.of("prevResult", "value1"), Map.of(), NOW);

      Map<String, Object> trigger =
          Map.of(
              "id", DATASET_ID,
              "name", "Test",
              "customField", "customValue");

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              trigger,
              List.of(
                  completedStep, SagaStep.pending("custom-step", "custom-adapter", "CUSTOM_OP")));

      Map<String, Object> payload = DatasetCommandBuilder.buildExecutePayload(context, unknownStep);

      assertEquals("CUSTOM_OP", payload.get("operation"));
      assertEquals(DATASET_ID, payload.get("datasetId"));
      assertEquals("customValue", payload.get("customField"));
      assertEquals("value1", payload.get("prevResult"));
    }

    @Test
    @DisplayName("Generic compensation payload includes result and compensationData")
    void buildCompensatePayload_unknownAdapter_shouldIncludeResultAndCompData() {
      SagaStep step =
          SagaStep.pending("custom-step", "custom-adapter", "CUSTOM_OP")
              .asSucceeded(Map.of("resultKey", "resultVal"), Map.of("compKey", "compVal"), NOW);

      SagaContext context = createContextWithStep(SagaType.DATASET_CREATE, step);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildCompensatePayload(context, step, unknownStep);

      assertEquals("UNDO_CUSTOM_OP", payload.get("operation"));
      assertEquals("resultVal", payload.get("resultKey"));
      assertEquals("compVal", payload.get("compKey"));
    }
  }

  // ─── aggregateSagaResult ────────────────────────────────────────────────────

  @Nested
  @DisplayName("Saga result aggregation")
  class SagaResultAggregation {

    @Test
    @DisplayName("Aggregates results from all completed steps with properties array")
    void aggregateSagaResult_allStepsComplete_shouldAggregateResults() {
      SagaStep frostStep =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asSucceeded(
                  Map.of("projectId", "proj-1", "baseUrl", "http://frost/proj-1"), Map.of(), NOW);

      SagaStep apisixStep =
          SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")
              .asSucceeded(Map.of("routeId", "r-1", "serviceId", "s-1"), Map.of(), NOW);

      SagaStep redpandaStep =
          SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")
              .asSucceeded(Map.of("pipelineIds", List.of("pl-1", "pl-2")), Map.of(), NOW);

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test"),
              List.of(frostStep, apisixStep, redpandaStep));

      Map<String, Object> result = DatasetCommandBuilder.aggregateSagaResult(context);

      assertEquals(SAGA_ID, result.get("sagaId"));
      assertEquals(DATASET_ID, result.get("datasetId"));
      assertEquals("proj-1", result.get("projectId"));
      assertEquals("r-1", result.get("routeId"));
      assertEquals("s-1", result.get("serviceId"));
      assertEquals(List.of("pl-1", "pl-2"), result.get("pipelineIds"));

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> properties = (List<Map<String, Object>>) result.get("properties");
      assertNotNull(properties);
      assertEquals(4, properties.size());
      assertTrue(properties.contains(Map.of("projectId", "proj-1")));
      assertTrue(properties.contains(Map.of("routeId", "r-1")));
      assertTrue(properties.contains(Map.of("serviceId", "s-1")));
      assertTrue(properties.contains(Map.of("pipelineIds", List.of("pl-1", "pl-2"))));
    }

    @Test
    @DisplayName("Skips failed and pending steps in aggregation")
    void aggregateSagaResult_mixedStatuses_shouldOnlyIncludeSuccessful() {
      SagaStep successStep =
          SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
              .asSucceeded(Map.of("projectId", "proj-1"), Map.of(), NOW);

      SagaStep failedStep =
          SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")
              .asFailed("Connection timeout", NOW);

      SagaStep pendingStep = SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES");

      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test"),
              List.of(successStep, failedStep, pendingStep));

      Map<String, Object> result = DatasetCommandBuilder.aggregateSagaResult(context);

      assertEquals("proj-1", result.get("projectId"));
      assertNull(result.get("routeId"));

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> properties = (List<Map<String, Object>>) result.get("properties");
      assertEquals(1, properties.size());
      assertTrue(properties.contains(Map.of("projectId", "proj-1")));
    }

    @Test
    @DisplayName("Returns empty properties list when no steps completed")
    void aggregateSagaResult_noCompletedSteps_shouldReturnEmptyProperties() {
      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test"),
              List.of(SagaStep.pending("create-project", "frost", "CREATE_PROJECT")));

      Map<String, Object> result = DatasetCommandBuilder.aggregateSagaResult(context);

      assertEquals(SAGA_ID, result.get("sagaId"));
      assertEquals(DATASET_ID, result.get("datasetId"));

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> properties = (List<Map<String, Object>>) result.get("properties");
      assertTrue(properties.isEmpty());
    }
  }

  // ─── Property extraction ────────────────────────────────────────────────────

  @Nested
  @DisplayName("Property extraction from trigger payload")
  class PropertyExtraction {

    private final SagaStepDefinition updateProjectStep =
        SagaStepDefinition.mandatory(
            "update-project",
            "frost",
            "UPDATE_PROJECT",
            "RESTORE_PROJECT",
            "de.civitascore.dataset.frost.execute",
            "de.civitascore.dataset.frost.compensate");

    @Test
    @DisplayName("Extracts property from nested properties list")
    void buildExecutePayload_propertyInList_shouldExtractFromList() {
      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "Test",
              "properties",
              List.of(Map.of("projectId", "proj-from-props")));

      SagaContext context = createContext(SagaType.DATASET_UPDATE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, updateProjectStep);

      assertEquals("proj-from-props", payload.get("projectId"));
    }

    @Test
    @DisplayName("Falls back to direct access when properties list is absent")
    void buildExecutePayload_noPropertiesList_shouldFallbackToDirectAccess() {
      Map<String, Object> trigger =
          Map.of(
              "id", DATASET_ID,
              "name", "Test",
              "projectId", "proj-direct");

      SagaContext context = createContext(SagaType.DATASET_UPDATE, trigger);

      Map<String, Object> payload =
          DatasetCommandBuilder.buildExecutePayload(context, updateProjectStep);

      assertEquals("proj-direct", payload.get("projectId"));
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

  private static SagaContext createContext(SagaType sagaType, Map<String, Object> trigger) {
    return new SagaContext(
        SAGA_ID,
        sagaType,
        DATASET_ID,
        null,
        SagaStatus.EXECUTING,
        List.of(),
        null,
        trigger,
        NOW,
        NOW);
  }

  private static SagaContext createContextWithStep(SagaType sagaType, SagaStep step) {
    return new SagaContext(
        SAGA_ID,
        sagaType,
        DATASET_ID,
        step.stepId(),
        SagaStatus.EXECUTING,
        List.of(step),
        null,
        Map.of("id", DATASET_ID, "name", "Test"),
        NOW,
        NOW);
  }

  private static SagaContext createContextWithSteps(
      SagaType sagaType, Map<String, Object> trigger, List<SagaStep> steps) {
    return new SagaContext(
        SAGA_ID, sagaType, DATASET_ID, null, SagaStatus.EXECUTING, steps, null, trigger, NOW, NOW);
  }
}
