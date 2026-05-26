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

import static de.civitascore.configadapter.redpanda.RedpandaTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RedpandaSagaHandlerTest {

  @Test
  @DisplayName("adapter() returns 'redpanda'")
  void adapter_default_returnsRedpanda() {
    try (RedpandaSagaHandler handler = createHandler()) {
      assertEquals("redpanda", handler.adapter());
    }
  }

  @Test
  @DisplayName("fieldAliases() declares baseUrl→targetUrl so the saga forwards FROST's baseUrl")
  void fieldAliases_declaresBaseUrlToTargetUrl() {
    try (RedpandaSagaHandler handler = createHandler()) {
      assertEquals(Map.of("baseUrl", "targetUrl"), handler.fieldAliases());
    }
  }

  @Nested
  @DisplayName("DEPLOY_PIPELINES")
  class DeployPipelines {

    @Test
    @DisplayName("deploys single pipeline successfully")
    void handle_singlePipeline_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-1", "data", Map.of("input", Map.of())))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("saga-001", result.sagaId());
        assertNotNull(result.resultData().get("pipelineIds"));
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) result.resultData().get("pipelineIds");
        assertEquals(1, ids.size());
        assertEquals("pipeline-1", ids.get(0));
        verify(mockClient).createPipeline(eq("pipeline-1"), anyMap());
      }
    }

    @Test
    @DisplayName("deploys multiple pipelines successfully")
    void handle_multiplePipelines_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(
                        Map.of("id", "pipeline-1", "data", Map.of()),
                        Map.of("id", "pipeline-2", "data", Map.of()))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) result.resultData().get("pipelineIds");
        assertEquals(2, ids.size());
      }
    }

    @Test
    @DisplayName("returns STEP_FAILED without inline rollback — orchestrator owns compensation")
    void handle_secondPipelineFails_returnsStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(eq("pipeline-1"), anyMap());
      doThrow(new FatalAdapterException(AdapterErrorCode.REDPANDA_PIPELINE_ERROR, "test"))
          .when(mockClient)
          .createPipeline(eq("pipeline-2"), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(
                        Map.of("id", "pipeline-1", "data", Map.of()),
                        Map.of("id", "pipeline-2", "data", Map.of()))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        verify(mockClient, never()).deletePipeline(any());
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_PIPELINES")
  class UpdatePipelines {

    @Test
    @DisplayName("handles mixed ADD/UPDATE/DELETE actions")
    void handle_mixedActions_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());
      doNothing().when(mockClient).updatePipeline(any(), anyMap());
      doNothing().when(mockClient).deletePipeline(any());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(
                        Map.of("id", "p-new", "action", "ADD", "data", Map.of()),
                        Map.of("id", "p-existing", "action", "UPDATE", "data", Map.of()),
                        Map.of("id", "p-old", "action", "DELETE"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        verify(mockClient).createPipeline(eq("p-new"), anyMap());
        verify(mockClient).updatePipeline(eq("p-existing"), anyMap());
        verify(mockClient).deletePipeline("p-old");
      }
    }

    @Test
    @DisplayName("unknown action returns STEP_FAILED")
    void updatePipelines_unknownAction_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(Map.of("id", "p-1", "action", "INVALID"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("unknown action after valid action returns STEP_FAILED")
    void updatePipelines_unknownActionAfterValid_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(
                        Map.of("id", "p-1", "action", "ADD", "data", Map.of()),
                        Map.of("id", "p-2", "action", "INVALID"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  @DisplayName("DELETE_PIPELINES")
  class DeletePipelines {

    @Test
    @DisplayName("forward delete returns STEP_COMPLETED")
    void handle_forwardDelete_returnsStepCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).deletePipeline(any());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DELETE_PIPELINES",
                Map.of("datasetId", "ds-1", "pipelineIds", List.of("p-1", "p-2")));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
        verify(mockClient).deletePipeline("p-1");
        verify(mockClient).deletePipeline("p-2");
      }
    }

    @Test
    @DisplayName("compensate delete returns COMPENSATION_COMPLETED")
    void handle_compensateDelete_returnsCompensationCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).deletePipeline(any());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "DELETE_PIPELINES",
                Map.of("datasetId", "ds-1", "pipelineIds", List.of("p-1")));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("failure returns STEP_FAILED")
    void handle_deleteFails_returnsStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doThrow(new FatalAdapterException(AdapterErrorCode.REDPANDA_PIPELINE_ERROR, "test"))
          .when(mockClient)
          .deletePipeline(any());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DELETE_PIPELINES",
                Map.of("datasetId", "ds-1", "pipelineIds", List.of("p-1")));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("compensate failure returns COMPENSATION_FAILED")
    void handle_compensateDeleteFails_returnsCompensationFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doThrow(new FatalAdapterException(AdapterErrorCode.REDPANDA_PIPELINE_ERROR, "test"))
          .when(mockClient)
          .deletePipeline(any());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "DELETE_PIPELINES",
                Map.of("datasetId", "ds-1", "pipelineIds", List.of("p-1")));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("RESTORE_PIPELINES")
  class RestorePipelines {

    @Test
    @DisplayName("restores pipelines successfully")
    void handle_restoreRequest_returnsCompensationCompleted() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).updatePipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(Map.of("id", "p-1", "data", Map.of("input", Map.of())))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        verify(mockClient).updatePipeline(eq("p-1"), anyMap());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on error")
    void handle_restoreFails_returnsCompensationFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doThrow(new FatalAdapterException(AdapterErrorCode.REDPANDA_PIPELINE_ERROR, "test"))
          .when(mockClient)
          .updatePipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "dataPipelines",
                    List.of(Map.of("id", "p-1", "data", Map.of()))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  @DisplayName("Placeholder resolution")
  class PlaceholderResolution {

    @Test
    @DisplayName("deploys pipeline with ${FROST_BASE} resolved from targetUrl")
    void handle_deployWithFrostBasePlaceholder_resolvesTargetUrl() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipelineData =
            Map.of(
                "output",
                Map.of("http_client", Map.of("url", "${FROST_BASE}/Things", "verb", "POST")));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "targetUrl",
                    "https://frost.example.com/FROST-Server/v1.1",
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-frost", "data", pipelineData))));

        SagaCommandResult result = handler.handle(command);
        assertEquals("STEP_COMPLETED", result.type());

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockClient).createPipeline(eq("pipeline-frost"), captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> deployedOutput =
            (Map<String, Object>)
                ((Map<String, Object>) captor.getValue().get("output")).get("http_client");
        assertEquals(
            "https://frost.example.com/FROST-Server/v1.1/Things", deployedOutput.get("url"));
      }
    }

    @Test
    @DisplayName(
        "deploys pipeline with both DatasourceInjector label and PlaceholderResolver placeholders"
            + " resolved")
    void handle_deployWithLabelAndPlaceholders_resolvesBoth() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());
      when(mockClient.encryptDatasourceValue(anyString()))
          .thenAnswer(invocation -> "ENC(" + invocation.getArgument(0, String.class) + ")");

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        String datasourceId = "mqtt-ds-123";

        // Pipeline with:
        //  - input label (resolved by DatasourceInjector → injects MQTT config)
        //  - output ${FROST_BASE} (resolved by PlaceholderResolver)
        //  - processors ${DATASOURCE[0]} DSN (resolved by PlaceholderResolver)
        Map<String, Object> pipelineData = new HashMap<>();
        pipelineData.put("input", Map.of("label", "${" + datasourceId + "}"));
        pipelineData.put(
            "output", Map.of("http_client", Map.of("url", "${FROST_BASE}/Things", "verb", "POST")));
        pipelineData.put(
            "pipeline", Map.of("processors", List.of(Map.of("dsn", "${DATASOURCE[0]}"))));

        // MQTT datasource for DatasourceInjector
        Map<String, Object> mqttDsMap = new HashMap<>();
        mqttDsMap.put("id", datasourceId);
        mqttDsMap.put("type", "mqtt");
        mqttDsMap.put("host", "broker.local");
        mqttDsMap.put("port", 1883);
        mqttDsMap.put("configuration", Map.of("topics", List.of("sensor/#")));

        // Postgres datasource for ${DATASOURCE[0]} placeholder
        Map<String, Object> pgDsMap = new HashMap<>();
        pgDsMap.put("id", "pg-ds-1");
        pgDsMap.put("type", "postgresql");
        pgDsMap.put("host", "db.local");
        pgDsMap.put("port", 5432);
        pgDsMap.put("database", "testdb");
        pgDsMap.put("username", "admin");
        pgDsMap.put("password", "s3cret");
        pgDsMap.put("ssl_mode", "disable");

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "targetUrl",
                    "https://frost.example.com/FROST-Server/v1.1",
                    "datasources",
                    List.of(pgDsMap, mqttDsMap),
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-combined", "data", pipelineData))));

        SagaCommandResult result = handler.handle(command);
        assertEquals("STEP_COMPLETED", result.type());

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockClient).createPipeline(eq("pipeline-combined"), captor.capture());

        Map<String, Object> deployed = captor.getValue();

        // Verify DatasourceInjector resolved the input label → MQTT config
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) deployed.get("input");
        assertNotNull(input.get("mqtt"), "input should contain mqtt block after label resolution");

        // Verify PlaceholderResolver resolved ${FROST_BASE} in output
        @SuppressWarnings("unchecked")
        Map<String, Object> httpClient =
            (Map<String, Object>) ((Map<String, Object>) deployed.get("output")).get("http_client");
        assertEquals("https://frost.example.com/FROST-Server/v1.1/Things", httpClient.get("url"));

        // Verify PlaceholderResolver resolved ${DATASOURCE[0]} in processors
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processors =
            (List<Map<String, Object>>)
                ((Map<String, Object>) deployed.get("pipeline")).get("processors");
        String dsn = (String) processors.get(0).get("dsn");
        assertTrue(dsn.startsWith("ENC(postgres://admin"));
        assertTrue(dsn.contains("db.local:5432"));
        assertTrue(dsn.contains("testdb"));
      }
    }

    @Test
    @DisplayName("updates pipeline with ${FROST_BASE} resolved from targetUrl")
    void handle_updateWithFrostBasePlaceholder_resolvesTargetUrl() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).updatePipeline(any(), anyMap());

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipelineData =
            Map.of(
                "output",
                Map.of("http_client", Map.of("url", "${FROST_BASE}/Things", "verb", "POST")));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "targetUrl",
                    "https://frost.example.com/FROST-Server/v1.1",
                    "dataPipelines",
                    List.of(
                        Map.of("id", "pipeline-frost", "action", "UPDATE", "data", pipelineData))));

        SagaCommandResult result = handler.handle(command);
        assertEquals("STEP_COMPLETED", result.type());

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockClient).updatePipeline(eq("pipeline-frost"), captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> deployedOutput =
            (Map<String, Object>)
                ((Map<String, Object>) captor.getValue().get("output")).get("http_client");
        assertEquals(
            "https://frost.example.com/FROST-Server/v1.1/Things", deployedOutput.get("url"));
      }
    }

    @Test
    @DisplayName("restores pipeline with ${DATASOURCE[0]} resolved to DSN")
    void handle_restoreWithDatasourcePlaceholder_resolvesDsn() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).updatePipeline(any(), anyMap());
      when(mockClient.encryptDatasourceValue(anyString()))
          .thenAnswer(invocation -> "ENC(" + invocation.getArgument(0, String.class) + ")");

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipelineData =
            Map.of("pipeline", Map.of("processors", List.of(Map.of("dsn", "${DATASOURCE[0]}"))));

        Map<String, Object> datasource = new HashMap<>();
        datasource.put("id", "pg-ds-1");
        datasource.put("type", "postgresql");
        datasource.put("host", "db.local");
        datasource.put("port", 5432);
        datasource.put("database", "testdb");
        datasource.put("username", "admin");
        datasource.put("password", "s3cret");
        datasource.put("ssl_mode", "disable");

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "datasources",
                    List.of(datasource),
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-dsn", "data", pipelineData))));

        SagaCommandResult result = handler.handle(command);
        assertEquals("COMPENSATION_COMPLETED", result.type());

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockClient).updatePipeline(eq("pipeline-dsn"), captor.capture());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processors =
            (List<Map<String, Object>>)
                ((Map<String, Object>) captor.getValue().get("pipeline")).get("processors");
        String dsn = (String) processors.get(0).get("dsn");
        assertEquals("ENC(postgres://admin:s3cret@db.local:5432/testdb?sslmode=disable)", dsn);
      }
    }

    @Test
    @DisplayName("deploys pipeline with ${DATASOURCE[0]} resolved to DSN")
    void handle_deployWithDatasourcePlaceholder_resolvesDsn() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());
      when(mockClient.encryptDatasourceValue(anyString()))
          .thenAnswer(invocation -> "ENC(" + invocation.getArgument(0, String.class) + ")");

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipelineData =
            Map.of("pipeline", Map.of("processors", List.of(Map.of("dsn", "${DATASOURCE[0]}"))));

        Map<String, Object> datasource = new HashMap<>();
        datasource.put("id", "pg-ds-1");
        datasource.put("type", "postgresql");
        datasource.put("host", "db.local");
        datasource.put("port", 5432);
        datasource.put("database", "testdb");
        datasource.put("username", "admin");
        datasource.put("password", "s3cret");
        datasource.put("ssl_mode", "disable");

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "datasources",
                    List.of(datasource),
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-dsn", "data", pipelineData))));

        SagaCommandResult result = handler.handle(command);
        assertEquals("STEP_COMPLETED", result.type());

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockClient).createPipeline(eq("pipeline-dsn"), captor.capture());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processors =
            (List<Map<String, Object>>)
                ((Map<String, Object>) captor.getValue().get("pipeline")).get("processors");
        String dsn = (String) processors.get(0).get("dsn");
        assertEquals("ENC(postgres://admin:s3cret@db.local:5432/testdb?sslmode=disable)", dsn);
      }
    }

    @Test
    @DisplayName("deploys pipeline with encrypted datasource password preserved as encoded in DSN")
    void handle_deployWithEncryptedPassword_keepsEncodedPasswordInDsn() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
      doNothing().when(mockClient).createPipeline(any(), anyMap());
      when(mockClient.encryptDatasourceValue(anyString()))
          .thenAnswer(invocation -> "ENC(" + invocation.getArgument(0, String.class) + ")");

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        // Override the default pass-through: simulate actual decryption of ENC(...) values
        when(mockClient.decryptDatasourceCredentials(anyMap()))
            .thenAnswer(
                invocation -> {
                  @SuppressWarnings("unchecked")
                  Map<String, Object> props = invocation.getArgument(0);
                  Map<String, Object> result = new HashMap<>(props);
                  if (result.containsKey("configuration")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cfg =
                        new HashMap<>((Map<String, Object>) result.get("configuration"));
                    if (cfg.get("password") instanceof String s && s.startsWith("ENC(")) {
                      cfg.put("password", "decrypted%2Fpass");
                    }
                    result.put("configuration", cfg);
                  }
                  return result;
                });
        Map<String, Object> pipelineData = Map.of("input", Map.of("label", "${sql-ds-1}"));

        Map<String, Object> datasource = new HashMap<>();
        datasource.put("id", "sql-ds-1");
        datasource.put("type", "postgresql");
        datasource.put(
            "configuration",
            Map.of(
                "host", "db.local",
                "port", 5432,
                "database", "testdb",
                "username", "admin",
                "password", "ENC(fake-encrypted-payload)",
                "query", "SELECT * FROM sensors"));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "targetUrl",
                    "https://frost.example.com/FROST-Server/v1.1",
                    "datasources",
                    List.of(datasource),
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-enc", "data", pipelineData))));

        SagaCommandResult result = handler.handle(command);
        assertEquals("STEP_COMPLETED", result.type());

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockClient).createPipeline(eq("pipeline-enc"), captor.capture());

        // Verify the DSN stays encrypted in the pipeline payload after rebuilding it
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) captor.getValue().get("input");
        @SuppressWarnings("unchecked")
        Map<String, Object> sqlRaw = (Map<String, Object>) input.get("sql_raw");
        assertNotNull(sqlRaw, "input should contain sql_raw block after label resolution");
        String dsn = (String) sqlRaw.get("dsn");
        assertTrue(dsn.startsWith("ENC("), "DSN should stay encrypted in pipeline payload");
        assertTrue(
            dsn.contains("decrypted%2Fpass"),
            "encrypted DSN payload should contain encoded decrypted password before final send");
      }
    }
  }

  @Nested
  @DisplayName("Unknown operation")
  class UnknownOperation {

    @Test
    @DisplayName("returns STEP_FAILED for unknown forward operation")
    void handle_unknownForwardOperation_returnsStepFailed() {
      try (RedpandaSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "UNKNOWN_OP", Map.of());

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED for unknown compensate operation")
    void handle_unknownCompensateOperation_returnsCompensationFailed() {
      try (RedpandaSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("COMPENSATE_STEP", "UNKNOWN_OP", Map.of());

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("Payload validation edge cases")
  class PayloadValidation {

    @Test
    @DisplayName("DEPLOY_PIPELINES with null pipeline id returns STEP_FAILED")
    void deployPipelines_nullId_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("id", null);
        pipeline.put("data", Map.of());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of("datasetId", "ds-1", "dataPipelines", List.of(pipeline)));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockClient, never()).createPipeline(any(), anyMap());
      }
    }

    @Test
    @DisplayName("UPDATE_PIPELINES with null action returns STEP_FAILED")
    void updatePipelines_nullAction_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("id", "p-1");
        pipeline.put("action", null);
        pipeline.put("data", Map.of());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PIPELINES",
                Map.of("datasetId", "ds-1", "dataPipelines", List.of(pipeline)));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("DEPLOY_PIPELINES with dataPipelines as String returns STEP_FAILED")
    void deployPipelines_invalidPayloadType_shouldReturnStepFailed() {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of("datasetId", "ds-1", "dataPipelines", "not-a-list"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("DEPLOY_PIPELINES with data field not a Map returns STEP_FAILED")
    void deployPipelines_dataFieldNotAMap_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("id", "pipeline-1");
        pipeline.put("data", "not-a-map");

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of("datasetId", "ds-1", "dataPipelines", List.of(pipeline)));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockClient, never()).createPipeline(any(), anyMap());
      }
    }

    @Test
    @DisplayName("DEPLOY_PIPELINES with list of strings returns STEP_FAILED")
    void deployPipelines_listOfStrings_shouldReturnStepFailed() {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of("datasetId", "ds-1", "dataPipelines", List.of("not-a-map")));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("DEPLOY_PIPELINES with malformed datasource entry returns STEP_FAILED")
    void deployPipelines_malformedDatasourceEntry_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "datasources",
                    List.of("not-a-datasource-map"),
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-1", "data", Map.of()))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockClient, never()).createPipeline(any(), anyMap());
      }
    }

    @Test
    @DisplayName("DEPLOY_PIPELINES with valid then invalid datasource returns STEP_FAILED")
    void deployPipelines_validThenInvalidDatasource_shouldReturnStepFailed() throws Exception {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        Map<String, Object> validDs = new HashMap<>();
        validDs.put("id", "ds-ok");
        validDs.put("type", "mqtt");
        validDs.put("host", "broker.local");

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DEPLOY_PIPELINES",
                Map.of(
                    "datasetId",
                    "ds-1",
                    "datasources",
                    List.of(validDs, "not-a-map"),
                    "dataPipelines",
                    List.of(Map.of("id", "pipeline-1", "data", Map.of()))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockClient, never()).createPipeline(any(), anyMap());
      }
    }

    @Test
    @DisplayName("DELETE_PIPELINES with list of integers returns STEP_FAILED")
    void deletePipelines_listOfIntegers_shouldReturnStepFailed() {
      RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);

      try (RedpandaSagaHandler handler = createHandlerWithClient(mockClient)) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "DELETE_PIPELINES",
                Map.of("datasetId", "ds-1", "pipelineIds", List.of(123, 456)));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  private RedpandaSagaHandler createHandler() {
    RedpandaConnectClient mockClient = mock(RedpandaConnectClient.class);
    return createHandlerWithClient(mockClient);
  }

  private RedpandaSagaHandler createHandlerWithClient(RedpandaConnectClient mockClient) {
    RedpandaSagaHandler handler = new RedpandaSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("redpanda.url", "http://localhost:4195"))
        .thenReturn("http://localhost:4195");

    // Pass-through: datasource credential decryption is a no-op in unit tests
    try {
      when(mockClient.decryptDatasourceCredentials(anyMap()))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(mockClient.encryptDatasourceValue(anyString()))
          .thenAnswer(invocation -> "ENC(" + invocation.getArgument(0, String.class) + ")");
    } catch (FatalAdapterException e) {
      throw new RuntimeException(e);
    }

    handler.setTestRedpandaClient(mockClient);
    handler.initialize(mockConfig);
    return handler;
  }
}
