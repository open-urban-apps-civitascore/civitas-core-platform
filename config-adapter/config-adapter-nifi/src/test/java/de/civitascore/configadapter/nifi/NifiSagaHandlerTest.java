/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NifiSagaHandlerTest {

  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private final ObjectMapper mapper = new ObjectMapper();
  private NifiRestClient restClient;
  private NifiSagaHandler handler;

  @BeforeEach
  void setUp() {
    AdapterConfig config = mock(AdapterConfig.class);
    when(config.getProperty(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1)); // return defaults
    when(config.getProperty("nifi.master-key", null)).thenReturn(MASTER_KEY_HEX);
    // platform sink connections the planner now requires (FROST base URL / PostGIS DB URL)
    when(config.getProperty("nifi.frost.url", null))
        .thenReturn("http://frost:8080/FROST-Server/v1.1");
    when(config.getProperty("nifi.postgis.url", null))
        .thenReturn("jdbc:postgresql://db:5432/civitas");

    restClient = mock(NifiRestClient.class);
    handler = new NifiSagaHandler();
    handler.setTestNifiClient(restClient);
    handler.initialize(config);
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private SagaCommandMessage deployCommand() throws Exception {
    Map<String, Object> payload =
        map(
            """
            {
              "type": "EXECUTE_STEP",
              "sagaId": "saga-1",
              "stepId": "deploy-pipelines",
              "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "datasources": [
                { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] }
              ],
              "datasinks": [
                { "id": "sk-1", "type": "POSTGIS",
                  "configuration": { "tableName": "obs" },
                  "dataStructure": { "type": "object", "properties": { "id": { "type": "string" } },
                    "required": ["id"] } }
              ],
              "dataPipelines": [
                { "id": "p-1", "version": "1", "action": "ADD",
                  "data": { "nodes": [
                      { "id": "s", "type": "start", "data": {} },
                      { "id": "m", "type": "mapping",
                        "data": { "mappingConfig": { "fields": { "$.id": "$.id" } } } },
                      { "id": "e", "type": "end", "data": {} } ],
                    "edges": [
                      { "id": "e1", "source": "s", "target": "m" },
                      { "id": "e2", "source": "m", "target": "e" } ] } }
              ]
            }
            """);
    return SagaCommandMessage.fromMap(payload);
  }

  @Test
  void deployDispatchesToClientAndReportsIds() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-99");

    SagaCommandResult result = handler.handle(deployCommand());

    assertEquals("STEP_COMPLETED", result.type());
    assertEquals(List.of("p-1"), result.resultData().get("pipelineIds"));
    assertEquals(List.of("pg-99"), result.resultData().get("nifiProcessGroupIds"));

    verify(restClient, times(1)).deployFlow(any(DeploymentPlan.class));
  }

  @Test
  void deleteDispatchesByProcessGroupName() throws Exception {
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "delete",
              "adapter": "nifi", "operation": "DELETE_PIPELINES",
              "pipelineIds": ["p-1", "p-2"] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_COMPLETED", result.type());
    verify(restClient).deleteFlowByName(eq("pipeline-p-1"));
    verify(restClient).deleteFlowByName(eq("pipeline-p-2"));
  }

  @Test
  void deployWithoutDatasinkDefaultsToFrost() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-frost");
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "saga-frost", "stepId": "deploy-pipelines",
              "adapter": "nifi", "operation": "DEPLOY_PIPELINES",
              "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
              "datasinks": [],
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "data": { "nodes": [
                    { "id": "s", "type": "start", "data": {} },
                    { "id": "m", "type": "mapping",
                      "data": { "mappingConfig": { "fields": { "$.id": "$.id" } } } },
                    { "id": "e", "type": "end", "data": {} } ],
                  "edges": [
                    { "id": "e1", "source": "s", "target": "m" },
                    { "id": "e2", "source": "m", "target": "e" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_COMPLETED", result.type());
    verify(restClient).deployFlow(any(DeploymentPlan.class));
  }

  @Test
  void deployFailureBecomesStepFailed() throws Exception {
    when(restClient.deployFlow(any()))
        .thenThrow(
            new de.civitascore.configadapter.exception.RetryableAdapterException(
                de.civitascore.configadapter.model.AdapterErrorCode.NIFI_ERROR, "boom"));

    SagaCommandResult result = handler.handle(deployCommand());

    assertEquals("STEP_FAILED", result.type());
    assertTrue(result.error().contains("DEPLOY_PIPELINES failed"));
  }

  @Test
  void unknownOperationIsReported() throws Exception {
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "x",
              "adapter": "nifi", "operation": "FROBNICATE" }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));
    assertEquals("STEP_FAILED", result.type());
  }

  @Test
  void adapterNameIsNifi() {
    assertEquals("nifi", handler.adapter());
  }
}
