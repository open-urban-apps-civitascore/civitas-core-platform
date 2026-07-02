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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.mockito.ArgumentCaptor;

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
              "projectId": "5",
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

  /**
   * A DEPLOY command with one POSTGIS datasink built from the given configuration + dataStructure.
   */
  private SagaCommandMessage deployWithSink(String configJson, String dataStructureJson)
      throws Exception {
    return SagaCommandMessage.fromMap(
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
              "datasinks": [ { "id": "sk-1", "type": "POSTGIS", "configuration": %s, "dataStructure": %s } ],
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD", "data": {
                  "nodes": [ { "id": "s", "type": "start", "data": {} },
                    { "id": "m", "type": "mapping", "data": { "mappingConfig": { "fields": { "$.id": "$.id" } } } },
                    { "id": "e", "type": "end", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "s", "target": "m" },
                    { "id": "e2", "source": "m", "target": "e" } ] } } ] }
            """
                .formatted(configJson, dataStructureJson)));
  }

  /** Deploys the command and returns the flow snapshot handed to the REST client. */
  private String capturedSnapshot(SagaCommandMessage command) throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-1");
    handler.handle(command);
    ArgumentCaptor<DeploymentPlan> plan = ArgumentCaptor.forClass(DeploymentPlan.class);
    verify(restClient).deployFlow(plan.capture());
    return plan.getValue().snapshotJson();
  }

  @Test
  void deployDerivesUpsertKeysFromDataStructureMarker() throws Exception {
    // the cross-adapter "single source of truth": NiFi Update Keys come from x-core-primaryKey,
    // exactly like the PostGIS PRIMARY KEY
    String snapshot =
        capturedSnapshot(
            deployWithSink(
                "{ \"tableName\": \"obs\" }",
                "{ \"properties\": { \"id\": { \"type\": \"string\", \"x-core-primaryKey\": true },"
                    + " \"v\": { \"type\": \"string\" } } }"));
    assertTrue(snapshot.contains("\"Statement Type\":\"UPSERT\""), "UPSERT enabled by marker");
    assertTrue(snapshot.contains("\"Update Keys\":\"id\""), "Update Keys from marker");
  }

  @Test
  void nonStringExplicitPrimaryKeyEntryIsRejected() throws Exception {
    // a numeric/blank configuration.primaryKey entry must fail as INVALID_PAYLOAD, not be
    // String.valueOf'd into a bogus UPSERT key
    SagaCommandResult result =
        handler.handle(
            deployWithSink(
                "{ \"tableName\": \"obs\", \"primaryKey\": [42] }",
                "{ \"properties\": { \"id\": { \"type\": \"string\" } } }"));
    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void explicitPrimaryKeyOverridesDataStructureMarker() throws Exception {
    String snapshot =
        capturedSnapshot(
            deployWithSink(
                "{ \"tableName\": \"obs\", \"primaryKey\": [\"v\"] }",
                "{ \"properties\": { \"id\": { \"type\": \"string\", \"x-core-primaryKey\": true },"
                    + " \"v\": { \"type\": \"string\" } } }"));
    assertTrue(
        snapshot.contains("\"Update Keys\":\"v\""), "explicit primaryKey wins over the marker");
  }

  @Test
  void emptyExplicitPrimaryKeyFallsBackToDataStructureMarker() throws Exception {
    // an explicit empty list must behave like absent (schema fallback) — otherwise NiFi would
    // INSERT while PostGIS derives a PRIMARY KEY → divergence
    String snapshot =
        capturedSnapshot(
            deployWithSink(
                "{ \"tableName\": \"obs\", \"primaryKey\": [] }",
                "{ \"properties\": { \"id\": { \"type\": \"string\", \"x-core-primaryKey\": true } } }"));
    assertTrue(snapshot.contains("\"Update Keys\":\"id\""), "empty list falls back to the marker");
  }

  @Test
  void presentButNonObjectPipelineDataIsRejected() throws Exception {
    // a null 'data' is a valid provide-style pipeline (empty graph), but a present-but-non-object
    // 'data' is corrupt — coercing it to an empty graph would deploy a bare flow the user never
    // described, so it must fail loud rather than silently.
    SagaCommandResult result =
        handler.handle(
            SagaCommandMessage.fromMap(
                map(
                    """
                    { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
                      "operation": "DEPLOY_PIPELINES",
                      "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
                      "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD", "data": "not-an-object" } ] }
                    """)));
    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
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
              "projectId": "5",
              "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
              "datasinks": [],
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "data": { "nodes": [
                    { "id": "s", "type": "start", "data": {} },
                    { "id": "e", "type": "end", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "s", "target": "e" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    // a FROST default sink consumes the raw STA envelope — no mapping node (a mapping would be
    // rejected; see frostSinkWithMappingIsRejected in FlowDeploymentPlannerTest)
    assertEquals("STEP_COMPLETED", result.type());
    ArgumentCaptor<DeploymentPlan> plan = ArgumentCaptor.forClass(DeploymentPlan.class);
    verify(restClient).deployFlow(plan.capture());
    // the saga's projectId scopes the flow to the dataset's FROST project
    assertTrue(plan.getValue().snapshotJson().contains("/Projects(5)/Things"));
  }

  @Test
  void updateThreadsProjectIdIntoRedeployedFrostFlow() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-frost-upd");
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "saga-upd", "stepId": "update-pipelines",
              "adapter": "nifi", "operation": "UPDATE_PIPELINES",
              "projectId": "9",
              "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
              "datasinks": [],
              "dataPipelines": [ { "id": "p-1", "version": "2", "action": "UPDATE",
                "data": { "nodes": [
                    { "id": "s", "type": "start", "data": {} },
                    { "id": "e", "type": "end", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "s", "target": "e" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_COMPLETED", result.type());
    ArgumentCaptor<DeploymentPlan> plan = ArgumentCaptor.forClass(DeploymentPlan.class);
    verify(restClient).deployFlow(plan.capture());
    assertTrue(plan.getValue().snapshotJson().contains("/Projects(9)/Things"));
  }

  @Test
  void frostDeployWithNonNumericProjectIdFailsTheStep() throws Exception {
    // A present-but-non-numeric id must fail the step through the safe-external-message path
    // (never deploy, never leak the raw value across the result topic).
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "saga-frost", "stepId": "deploy-pipelines",
              "adapter": "nifi", "operation": "DEPLOY_PIPELINES",
              "projectId": "1) or true",
              "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
              "datasinks": [],
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "data": { "nodes": [
                    { "id": "s", "type": "start", "data": {} },
                    { "id": "e", "type": "end", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "s", "target": "e" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    assertFalse(result.error().contains("1) or true"), "raw payload value must not leak");
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void frostDeployWithoutProjectIdFailsTheStep() throws Exception {
    // Without the FROST create-project step's result the flow would post to the server root,
    // invisible through the dataset's named API — fail the saga instead of deploying it.
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
                    { "id": "e", "type": "end", "data": {} } ],
                  "edges": [ { "id": "e1", "source": "s", "target": "e" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
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
  void publishedFailureCarriesOnlySafeExternalMessageNotInternalDetail() throws Exception {
    // The internal message would leak NiFi's raw response (hostnames, DB URL, "Invalid SNI") into
    // the published failure event. Only the safe external message may cross the trust boundary.
    when(restClient.deployFlow(any()))
        .thenThrow(
            new de.civitascore.configadapter.exception.RetryableAdapterException(
                de.civitascore.configadapter.model.AdapterErrorCode.NIFI_ERROR,
                "authenticate: connect to civitas-nifi:8443 failed — Invalid SNI, db civitas-db:5432"));

    SagaCommandResult result = handler.handle(deployCommand());

    assertEquals("STEP_FAILED", result.type());
    assertTrue(
        result.error().contains("Pipeline service error"),
        "published error must be the safe external message");
    assertFalse(result.error().contains("civitas-nifi"), "internal host detail must not leak");
    assertFalse(result.error().contains("Invalid SNI"), "internal NiFi body must not leak");
    assertFalse(result.error().contains("civitas-db"), "internal DB detail must not leak");
  }

  @Test
  void deleteWithNonStringPipelineIdFailsBeforeDeletingAnything() throws Exception {
    // A malformed pipelineIds element must fail the step cleanly (INVALID_PAYLOAD) up front — never
    // after some pipelines have already been deleted by a cast that blows up mid-loop.
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "delete",
              "adapter": "nifi", "operation": "DELETE_PIPELINES",
              "pipelineIds": ["p-1", 42] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deleteFlowByName(any());
  }

  @Test
  void deployWithNonObjectDatasinkEntryFailsCleanly() throws Exception {
    // A malformed datasinks element (not an object) must fail the step (INVALID_PAYLOAD) rather
    // than
    // be silently dropped, which would deploy against an incomplete sink set yet report success.
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "deploy",
              "adapter": "nifi", "operation": "DEPLOY_PIPELINES",
              "datasinks": ["not-an-object"],
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "graphData": {}, "datasources": [] } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void blankPipelineIdIsRejected() throws Exception {
    // a blank id would build a "pipeline-" process-group name that could collide/mis-target —
    // reject
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES", "datasinks": [],
              "datasources": [ { "id": "ds-1", "type": "MQTT", "urls": ["tcp://m:1883"], "topics": ["t/+"] } ],
              "dataPipelines": [ { "id": "  ", "version": "1", "action": "ADD",
                "data": { "nodes": [], "edges": [] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
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
