/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStatus;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaType;
import de.civitascore.configadapter.orchestrator.engine.DatasetCommandBuilder;
import de.civitascore.configadapter.orchestrator.engine.SagaDefinition;
import de.civitascore.configadapter.orchestrator.engine.SagaDefinitions;
import de.civitascore.configadapter.orchestrator.engine.SagaStepDefinition;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests verifying that {@link RedpandaSagaHandler} can process payloads produced by {@link
 * DatasetCommandBuilder}. These tests use the real builder output to construct commands, ensuring
 * that payload format changes in the orchestrator are immediately caught.
 */
class RedpandaSagaContractTest {

  private static final String DATASET_ID = "ds-contract-001";
  private static final String SAGA_ID = "saga-contract-001";
  private static final Instant NOW = Instant.parse("2026-01-20T12:00:00Z");

  @Nested
  @DisplayName("DEPLOY_PIPELINES contract (DATASET_CREATE)")
  class DeployPipelinesContract {

    @Test
    @DisplayName("handler processes builder payload for single pipeline")
    void handle_singlePipelinePayload_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      List<Map<String, Object>> pipelines =
          List.of(
              Map.of(
                  "id",
                  "pl-mqtt-1",
                  "data",
                  Map.of("input", Map.of("mqtt", Map.of("urls", List.of("tcp://broker:1883"))))));

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_CREATE);
      SagaContext context = createDeployContext(pipelines);
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);

      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(SAGA_ID, result.sagaId());
        assertNotNull(result.resultData().get("pipelineIds"));

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) result.resultData().get("pipelineIds");
        assertEquals(1, ids.size());
        assertEquals("pl-mqtt-1", ids.get(0));
        verify(mockClient).createPipeline(any(), anyMap());
      }
    }

    @Test
    @DisplayName("handler processes builder payload for multiple pipelines")
    void handle_multiplePipelinePayload_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      List<Map<String, Object>> pipelines =
          List.of(
              Map.of("id", "pl-1", "data", Map.of("input", Map.of())),
              Map.of("id", "pl-2", "data", Map.of("input", Map.of())));

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_CREATE);
      SagaContext context = createDeployContext(pipelines);
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);

      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) result.resultData().get("pipelineIds");
        assertEquals(2, ids.size());
        assertTrue(ids.contains("pl-1"));
        assertTrue(ids.contains("pl-2"));
      }
    }

    @Test
    @DisplayName("builder payload with empty dataPipelines results in success with zero pipelines")
    void handle_emptyDataPipelines_returnsStepCompletedWithZeroPipelines() {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_CREATE);
      SagaContext context =
          createContext(SagaType.DATASET_CREATE, Map.of("id", DATASET_ID, "name", "Empty"));
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);

      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) result.resultData().get("pipelineIds");
        assertTrue(ids.isEmpty());
      }
    }

    @Test
    @DisplayName("result contains pipelineIds for aggregateSagaResult")
    void handle_deployPayload_resultContainsPipelineIds() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      List<Map<String, Object>> pipelines = List.of(Map.of("id", "pl-agg", "data", Map.of()));

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_CREATE);
      SagaContext context = createDeployContext(pipelines);
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);
      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        // aggregateSagaResult collects pipelineIds from resultData
        assertTrue(result.resultData().containsKey("pipelineIds"));
        // compensationData also contains pipelineIds for rollback
        assertTrue(result.compensationData().containsKey("pipelineIds"));
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_PIPELINES contract (DATASET_UPDATE)")
  class UpdatePipelinesContract {

    @Test
    @DisplayName("handler processes builder payload with mixed actions")
    void handle_mixedActionPayload_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());
      doNothing().when(mockClient).updatePipeline(any(), anyMap());
      doNothing().when(mockClient).deletePipeline(any());

      List<Map<String, Object>> pipelines =
          List.of(
              Map.of("id", "p-new", "action", "ADD", "data", Map.of("input", Map.of())),
              Map.of("id", "p-existing", "action", "UPDATE", "data", Map.of("input", Map.of())),
              Map.of("id", "p-old", "action", "DELETE"));

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_UPDATE);
      SagaContext context = createUpdateContext(pipelines);
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);

      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertTrue(result.resultData().containsKey("pipelineIds"));

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) result.resultData().get("pipelineIds");
        assertEquals(3, ids.size());
      }
    }
  }

  @Nested
  @DisplayName("DELETE_PIPELINES contract (DATASET_DELETE)")
  class DeletePipelinesContract {

    @Test
    @DisplayName("handler processes builder payload with pipelineIds from properties")
    void handle_pipelineIdsFromProperties_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).deletePipeline(any());

      List<String> pipelineIds = List.of("pl-1", "pl-2", "pl-3");
      Map<String, Object> trigger =
          Map.of(
              "id",
              DATASET_ID,
              "name",
              "To Delete",
              "properties",
              List.of(Map.of("pipelineIds", pipelineIds)));

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_DELETE);
      SagaContext context = createContext(SagaType.DATASET_DELETE, trigger);
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);

      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
        verify(mockClient).deletePipeline("pl-1");
        verify(mockClient).deletePipeline("pl-2");
        verify(mockClient).deletePipeline("pl-3");
      }
    }
  }

  @Nested
  @DisplayName("Compensation contracts")
  class CompensationContracts {

    @Test
    @DisplayName("DEPLOY compensation (DELETE_PIPELINES) uses pipelineIds from step result")
    void handle_deployCompensation_deletesDeployedPipelines() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).deletePipeline(any());

      // Simulate: deploy step succeeded with pipelineIds in result
      SagaStep deployedStep =
          SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")
              .asSucceeded(
                  Map.of("pipelineIds", List.of("pl-1", "pl-2")),
                  Map.of("pipelineIds", List.of("pl-1", "pl-2")),
                  NOW);

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_CREATE);
      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_CREATE,
              Map.of("id", DATASET_ID, "name", "Test", "datasources", List.of()),
              List.of(
                  SagaStep.pending("create-project", "frost", "CREATE_PROJECT"),
                  SagaStep.pending("create-route", "apisix", "CREATE_ROUTE"),
                  deployedStep));

      Map<String, Object> compensationPayload =
          DatasetCommandBuilder.buildCompensatePayload(context, deployedStep, stepDef);

      // compensationOperation for deploy is DELETE_PIPELINES
      assertEquals("DELETE_PIPELINES", compensationPayload.get("operation"));

      SagaCommandMessage command = toCommand("COMPENSATE_STEP", stepDef, compensationPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals(SAGA_ID, result.sagaId());
        verify(mockClient).deletePipeline("pl-1");
        verify(mockClient).deletePipeline("pl-2");
      }
    }

    @Test
    @DisplayName(
        "UPDATE compensation (RESTORE_PIPELINES) processes dataPipelines from compensationData")
    void handle_updateCompensation_restoresOriginalPipelines() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).updatePipeline(any(), anyMap());

      // In a real scenario, the handler's forward UPDATE step should store
      // dataPipelines in compensationData. Verify the contract handles this.
      List<Map<String, Object>> originalPipelines =
          List.of(Map.of("id", "p-1", "data", Map.of("input", Map.of("mqtt", Map.of()))));

      SagaStep updatedStep =
          SagaStep.pending("update-pipelines", "redpanda", "UPDATE_PIPELINES")
              .asSucceeded(
                  Map.of("pipelineIds", List.of("p-1")),
                  Map.of("pipelineIds", List.of("p-1"), "dataPipelines", originalPipelines),
                  NOW);

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_UPDATE);
      SagaContext context =
          createContextWithSteps(
              SagaType.DATASET_UPDATE,
              Map.of("id", DATASET_ID, "name", "Test"),
              List.of(
                  SagaStep.pending("update-project", "frost", "UPDATE_PROJECT"),
                  SagaStep.pending("update-route", "apisix", "UPDATE_ROUTE"),
                  updatedStep));

      Map<String, Object> compensationPayload =
          DatasetCommandBuilder.buildCompensatePayload(context, updatedStep, stepDef);

      assertEquals("RESTORE_PIPELINES", compensationPayload.get("operation"));
      // dataPipelines from compensationData should be in the payload
      assertTrue(compensationPayload.containsKey("dataPipelines"));

      SagaCommandMessage command = toCommand("COMPENSATE_STEP", stepDef, compensationPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        verify(mockClient).updatePipeline(any(), anyMap());
      }
    }
  }

  @Nested
  @DisplayName("Result format for aggregateSagaResult")
  class ResultAggregationContract {

    @Test
    @DisplayName("pipelineIds from handler result are collected by aggregateSagaResult")
    void handle_deployResult_pipelineIdsCollectedByAggregation() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      List<Map<String, Object>> pipelines =
          List.of(
              Map.of("id", "pl-agg-1", "data", Map.of()),
              Map.of("id", "pl-agg-2", "data", Map.of()));

      SagaStepDefinition stepDef = getRedpandaStepDef(SagaType.DATASET_CREATE);
      SagaContext context = createDeployContext(pipelines);
      Map<String, Object> builderPayload =
          DatasetCommandBuilder.buildExecutePayload(context, stepDef);
      SagaCommandMessage command = toCommand("EXECUTE_STEP", stepDef, builderPayload);

      try (RedpandaSagaHandler handler = createHandler(mockClient)) {
        SagaCommandResult result = handler.handle(command);

        // Simulate what the orchestrator does after receiving the result:
        // It stores result.resultData() in the SagaStep's result,
        // then aggregateSagaResult collects pipelineIds
        SagaStep frostStep =
            SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
                .asSucceeded(
                    Map.of("projectId", "proj-1", "baseUrl", "http://frost"), Map.of(), NOW);
        SagaStep apisixStep =
            SagaStep.pending("create-route", "apisix", "CREATE_ROUTE")
                .asSucceeded(Map.of("routeId", "r-1", "serviceId", "s-1"), Map.of(), NOW);
        SagaStep redpandaStep =
            SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")
                .asSucceeded(result.resultData(), result.compensationData(), NOW);

        SagaContext completedContext =
            new SagaContext(
                SAGA_ID,
                SagaType.DATASET_CREATE,
                DATASET_ID,
                null,
                SagaStatus.COMPLETED,
                List.of(frostStep, apisixStep, redpandaStep),
                null,
                Map.of("id", DATASET_ID, "name", "Test"),
                NOW,
                NOW);

        Map<String, Object> aggregated =
            DatasetCommandBuilder.aggregateSagaResult(completedContext);

        // Verify pipelineIds from handler result are in the aggregated result
        assertNotNull(aggregated.get("pipelineIds"));
        @SuppressWarnings("unchecked")
        List<String> pipelineIds = (List<String>) aggregated.get("pipelineIds");
        assertEquals(2, pipelineIds.size());
        assertTrue(pipelineIds.contains("pl-agg-1"));
        assertTrue(pipelineIds.contains("pl-agg-2"));

        // Verify pipelineIds appear in the properties array
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> properties =
            (List<Map<String, Object>>) aggregated.get("properties");
        assertTrue(properties.stream().anyMatch(p -> p.containsKey("pipelineIds")));
      }
    }
  }

  // ─── Step definition helpers ───────────────────────────────────────────────

  private static SagaStepDefinition getRedpandaStepDef(SagaType sagaType) {
    SagaDefinition definition = SagaDefinitions.forType(sagaType);
    return definition.steps().stream()
        .filter(s -> "redpanda".equals(s.adapter()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No redpanda step for " + sagaType));
  }

  // ─── SagaContext builders ──────────────────────────────────────────────────

  private static SagaContext createDeployContext(List<Map<String, Object>> pipelines) {
    Map<String, Object> trigger = new HashMap<>();
    trigger.put("id", DATASET_ID);
    trigger.put("name", "Test Dataset");
    trigger.put("openDataAccess", true);
    trigger.put("dataPipelines", pipelines);
    trigger.put("datasources", List.of(Map.of("id", "src-1", "type", "mqtt")));

    SagaStep frostStep =
        SagaStep.pending("create-project", "frost", "CREATE_PROJECT")
            .asSucceeded(
                Map.of("projectId", "proj-1", "baseUrl", "http://frost/proj-1"), Map.of(), NOW);

    return createContextWithSteps(
        SagaType.DATASET_CREATE,
        Map.copyOf(trigger),
        List.of(
            frostStep,
            SagaStep.pending("create-route", "apisix", "CREATE_ROUTE"),
            SagaStep.pending("deploy-pipelines", "redpanda", "DEPLOY_PIPELINES")));
  }

  private static SagaContext createUpdateContext(List<Map<String, Object>> pipelines) {
    Map<String, Object> trigger = new HashMap<>();
    trigger.put("id", DATASET_ID);
    trigger.put("name", "Updated Dataset");
    trigger.put("openDataAccess", true);
    trigger.put("dataPipelines", pipelines);
    trigger.put("datasources", List.of());

    SagaStep frostStep =
        SagaStep.pending("update-project", "frost", "UPDATE_PROJECT")
            .asSucceeded(Map.of("baseUrl", "http://frost/proj-1"), Map.of(), NOW);

    return createContextWithSteps(
        SagaType.DATASET_UPDATE,
        Map.copyOf(trigger),
        List.of(
            frostStep,
            SagaStep.pending("update-route", "apisix", "UPDATE_ROUTE"),
            SagaStep.pending("update-pipelines", "redpanda", "UPDATE_PIPELINES")));
  }

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

  private static SagaContext createContextWithSteps(
      SagaType sagaType, Map<String, Object> trigger, List<SagaStep> steps) {
    return new SagaContext(
        SAGA_ID, sagaType, DATASET_ID, null, SagaStatus.EXECUTING, steps, null, trigger, NOW, NOW);
  }

  // ─── Command construction ──────────────────────────────────────────────────

  /**
   * Constructs a {@link SagaCommandMessage} from the builder payload, simulating the flat map that
   * the orchestrator's {@code KafkaSagaActionDispatcher} would produce.
   */
  private static SagaCommandMessage toCommand(
      String type, SagaStepDefinition stepDef, Map<String, Object> builderPayload) {
    var flatMap = new HashMap<String, Object>(builderPayload);
    flatMap.put("type", type);
    flatMap.put("messageId", "msg-contract-001");
    flatMap.put("sagaId", SAGA_ID);
    flatMap.put("stepId", stepDef.stepId());
    flatMap.put("adapter", stepDef.adapter());
    flatMap.put("operation", stepDef.operation());

    // For compensation, the operation in the command is the compensation operation
    if ("COMPENSATE_STEP".equals(type) && stepDef.compensationOperation() != null) {
      flatMap.put("operation", stepDef.compensationOperation());
    }

    return SagaCommandMessage.fromMap(flatMap);
  }

  // ─── Handler construction ──────────────────────────────────────────────────

  private static RedpandaSagaHandler createHandler(RedpandaConnectClient mockClient) {
    RedpandaSagaHandler handler = new RedpandaSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("redpanda.url", "http://localhost:4195"))
        .thenReturn("http://localhost:4195");

    handler.setTestRedpandaClient(mockClient);
    handler.initialize(mockConfig);
    return handler;
  }
}
