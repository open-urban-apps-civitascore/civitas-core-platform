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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

class NifiSagaHandlerTest {

  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private final ObjectMapper mapper = new ObjectMapper();
  private AdapterConfig config;
  private NifiRestClient restClient;
  private NifiSagaHandler handler;

  @BeforeEach
  void setUp() {
    config = mock(AdapterConfig.class);
    when(config.getProperty(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1)); // return defaults
    when(config.getProperty("nifi.master-key", null)).thenReturn(MASTER_KEY_HEX);
    // platform sink connections the planner requires (FROST base URL / PostGIS DB URL)
    when(config.getProperty("nifi.frost.url", null))
        .thenReturn("http://frost:8080/FROST-Server/v1.1");
    when(config.getProperty("frost.basic.auth.username")).thenReturn("frost");
    when(config.getProperty("frost.basic.auth.password")).thenReturn("secret");
    when(config.getProperty("nifi.postgis.url", null))
        .thenReturn("jdbc:postgresql://db:5432/civitas");

    restClient = mock(NifiRestClient.class);
    handler = new NifiSagaHandler();
    handler.setTestNifiClient(restClient);
    handler.initialize(config);
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<>() {});
  }

  private Map<String, Object> deployPayload() throws Exception {
    return map(
        """
            {
              "type": "EXECUTE_STEP",
              "sagaId": "saga-1",
              "stepId": "deploy-pipelines",
              "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "projectId": "5",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "obs", "element": "el-1" },
                "el-1": { "type": "object", "properties": { "id": { "type": "string" } },
                  "required": ["id"] },
                "map-1": { "fields": { "$.id": "$.id" } }
              },
              "dataPipelines": [
                { "id": "p-1", "version": "1", "action": "ADD",
                  "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-1"],
                  "data": { "nodes": [
                      { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                      { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                      { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                    "edges": [
                      { "id": "e1", "source": "src", "target": "m" },
                      { "id": "e2", "source": "m", "target": "sink" } ] } }
              ]
            }
            """);
  }

  private SagaCommandMessage deployCommand() throws Exception {
    return SagaCommandMessage.fromMap(deployPayload());
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
  void initializeWiresOidcTokenProviderFromNifiOidcProperties() throws Exception {
    // Pins the nifi.oidc.* property-key contract. doInitialize() builds the real token provider
    // from
    // these keys and no other test exercises it (the rest inject the client), so a typo in a key or
    // default would pass the whole suite and only fail at deploy. Point token-uri + nifi.url at
    // WireMock, run a real deploy, and assert the token endpoint got the configured credentials.
    WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    try {
      server.stubFor(
          post(urlEqualTo("/realms/civitas-core/protocol/openid-connect/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"access_token\":\"tok\",\"expires_in\":300}")));
      // NiFi's first call fails fatally — we only need the preceding token fetch to have happened.
      server.stubFor(
          get(urlEqualTo("/nifi-api/process-groups/root")).willReturn(aResponse().withStatus(400)));

      AdapterConfig config = mock(AdapterConfig.class);
      when(config.getProperty(any(), any())).thenAnswer(inv -> inv.getArgument(1));
      when(config.getProperty("nifi.master-key", null)).thenReturn(MASTER_KEY_HEX);
      when(config.getProperty("nifi.frost.url", null))
          .thenReturn("http://frost:8080/FROST-Server/v1.1");
      when(config.getProperty("frost.basic.auth.username")).thenReturn("frost");
      when(config.getProperty("frost.basic.auth.password")).thenReturn("secret");
      when(config.getProperty("nifi.postgis.url", null))
          .thenReturn("jdbc:postgresql://db:5432/civitas");
      when(config.getProperty(eq("nifi.url"), any())).thenReturn(server.baseUrl());
      when(config.getProperty(eq("nifi.oidc.token-uri"), any()))
          .thenReturn(server.baseUrl() + "/realms/civitas-core/protocol/openid-connect/token");
      when(config.getProperty(eq("nifi.oidc.client-id"), any())).thenReturn("nifi-test-id");
      when(config.getProperty(eq("nifi.oidc.client-secret"), any())).thenReturn("nifi-test-secret");

      try (NifiSagaHandler realHandler = new NifiSagaHandler()) {
        realHandler.initialize(config); // no setTestNifiClient → real client + token provider
        realHandler.handle(deployCommand()); // deploy → authenticate() → token fetch, then NiFi 400

        server.verify(
            postRequestedFor(urlEqualTo("/realms/civitas-core/protocol/openid-connect/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=nifi-test-id"))
                .withRequestBody(containing("client_secret=nifi-test-secret")));
      }
    } finally {
      server.stop();
    }
  }

  private static final String INVALID_PAYLOAD_ERROR = "DEPLOY_PIPELINES failed: Validation failed";

  @SuppressWarnings("unchecked")
  private static Map<String, Object> artifacts(Map<String, Object> payload) {
    return (Map<String, Object>) payload.get("artifacts");
  }

  /**
   * The deploy command with its PostGIS sink configuration fields and the Data structure that the
   * sink's {@code element} names replaced by the given JSON objects.
   */
  private SagaCommandMessage deployWithSink(String configJson, String dataStructureJson)
      throws Exception {
    Map<String, Object> sink = map(configJson);
    sink.put("connectionType", "postgis");
    sink.put("element", "el-1");
    Map<String, Object> payload = deployPayload();
    artifacts(payload).put("sk-1", sink);
    artifacts(payload).put("el-1", map(dataStructureJson));
    return SagaCommandMessage.fromMap(payload);
  }

  private SagaCommandMessage deployCommandWith(String extra) throws Exception {
    Map<String, Object> payload = deployPayload();
    payload.putAll(map(extra));
    return SagaCommandMessage.fromMap(payload);
  }

  @Test
  void absentArtifactsFailsBeforeDeploying() throws Exception {
    Map<String, Object> payload = deployPayload();
    payload.remove("artifacts");

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void nonObjectArtifactsFailsBeforeDeploying() throws Exception {
    SagaCommandResult result = handler.handle(deployCommandWith("{ \"artifacts\": [\"ds-1\"] }"));

    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void nonObjectArtifactFailsBeforeDeploying() throws Exception {
    Map<String, Object> payload = deployPayload();
    artifacts(payload).put("unused", "document");

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ds-1", "sk-1", "map-1"})
  void nodeReferenceMissingFromArtifactsFailsBeforeDeploying(String ref) throws Exception {
    Map<String, Object> payload = deployPayload();
    artifacts(payload).remove(ref);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  private static final String PINNED_SOURCE =
      "urn:core:dataset:d:datasource:c:9787c3df-5325-8a02-8722-d44c626bed38:1.0.0";
  private static final String CURRENT_SOURCE =
      "urn:core:dataset:d:datasource:c:9787c3df-5325-8a02-8722-d44c626bed38:1.1.0";
  private static final String PINNED_SINK =
      "urn:core:dataset:d:datasink:c:5e0d3a41-7b2c-8f19-a6d4-3c2b1a0f9e8d:1.0.0";
  private static final String CURRENT_SINK =
      "urn:core:dataset:d:datasink:c:5e0d3a41-7b2c-8f19-a6d4-3c2b1a0f9e8d:1.1.0";

  /**
   * The deploy command with its source and sink nodes pinned to the 1.0.0 configurations and the
   * pipeline associated with the 1.1.0 ones, as after an edit of the Data source and the Data sink
   * that the pipeline was not saved again for. {@code artifacts} holds the current ones only.
   */
  private Map<String, Object> deployPayloadWithDriftedConfigurations() throws Exception {
    Map<String, Object> payload = deployPayload();
    Map<String, Object> artifacts = artifacts(payload);
    artifacts.put(
        CURRENT_SOURCE,
        map(
            "{ \"connectionType\": \"mqtt\", \"urls\": [\"tcp://m:1883\"], \"topics\": [\"t/current\"] }"));
    artifacts.put(
        CURRENT_SINK,
        map(
            "{ \"connectionType\": \"postgis\", \"tableName\": \"obs_current\", \"element\": \"el-1\" }"));
    String json =
        mapper
            .writeValueAsString(payload)
            .replace("\"sourceRef\":\"ds-1\"", "\"sourceRef\":\"" + PINNED_SOURCE + "\"")
            .replace("\"sinkRef\":\"sk-1\"", "\"sinkRef\":\"" + PINNED_SINK + "\"")
            .replace("[\"ds-1\"]", "[\"" + CURRENT_SOURCE + "\"]")
            .replace("[\"sk-1\"]", "[\"" + CURRENT_SINK + "\"]");
    Map<String, Object> drifted = map(json);
    artifacts(drifted).remove("ds-1");
    artifacts(drifted).remove("sk-1");
    return drifted;
  }

  @Test
  void deployUsesTheCurrentConfigurationsThatTheAssociationNames() throws Exception {
    Map<String, Object> payload = deployPayloadWithDriftedConfigurations();
    artifacts(payload)
        .put(
            PINNED_SOURCE,
            map(
                "{ \"connectionType\": \"mqtt\", \"urls\": [\"tcp://m:1883\"], \"topics\": [\"t/pinned\"] }"));
    artifacts(payload)
        .put(
            PINNED_SINK,
            map(
                "{ \"connectionType\": \"postgis\", \"tableName\": \"obs_pinned\", \"element\": \"el-1\" }"));
    when(restClient.deployFlow(any())).thenReturn("pg-1");

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_COMPLETED", result.type());
    ArgumentCaptor<DeploymentPlan> plan = ArgumentCaptor.forClass(DeploymentPlan.class);
    verify(restClient, times(1)).deployFlow(plan.capture());
    String snapshot = plan.getValue().snapshotJson();
    assertTrue(snapshot.contains("t/current"), "the current Data source configuration deploys");
    assertTrue(snapshot.contains("obs_current"), "the current Data sink configuration deploys");
    assertFalse(snapshot.contains("t/pinned"), "the pinned Data source configuration does not");
    assertFalse(snapshot.contains("obs_pinned"), "the pinned Data sink configuration does not");
  }

  @Test
  void nodeReferenceIsResolvedByExactUrnOnly() throws Exception {
    // Guards the fail-closed key contract: a logical-URN fallback would accept a node whose pinned
    // artifact the payload does not carry, because another version of it is there.
    SagaCommandResult result =
        handler.handle(SagaCommandMessage.fromMap(deployPayloadWithDriftedConfigurations()));

    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void currentConfigurationMissingFromArtifactsFailsBeforeDeploying() throws Exception {
    Map<String, Object> payload = deployPayloadWithDriftedConfigurations();
    artifacts(payload).put(PINNED_SOURCE, artifacts(payload).remove(CURRENT_SOURCE));
    artifacts(payload).put(PINNED_SINK, artifacts(payload).get(CURRENT_SINK));

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void sinkElementMissingFromArtifactsFailsBeforeDeploying() throws Exception {
    Map<String, Object> payload = deployPayload();
    artifacts(payload).remove("el-1");

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void deployWithMissingReferenceInALaterPipelineDeploysNothing() throws Exception {
    // A failed step stores no compensation data, so a flow deployed before the defective pipeline
    // would stay in NiFi.
    Map<String, Object> payload = deployPayload();
    Map<String, Object> second =
        map(
            """
            { "id": "p-2", "version": "1", "action": "ADD",
              "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-missing"],
              "data": { "nodes": [
                  { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                  { "id": "sink", "kind": "sink", "sinkRef": "sk-missing" } ],
                "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } }
            """);
    @SuppressWarnings("unchecked")
    List<Object> pipelines = (List<Object>) payload.get("dataPipelines");
    pipelines.add(second);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    assertEquals(INVALID_PAYLOAD_ERROR, result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void updateWithMissingReferenceInALaterPipelineChangesNothing() throws Exception {
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "u", "adapter": "nifi",
              "operation": "UPDATE_PIPELINES",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "obs" } },
              "dataPipelines": [
                { "id": "p-1", "version": "2", "action": "UPDATE",
                  "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-1"],
                  "data": { "nodes": [
                      { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                      { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                    "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } },
                { "id": "p-0", "version": "1", "action": "DELETE", "data": null,
                  "dataSourceIds": [], "dataSinkIds": [] },
                { "id": "p-2", "version": "1", "action": "ADD",
                  "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-missing"],
                  "data": { "nodes": [
                      { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                      { "id": "sink", "kind": "sink", "sinkRef": "sk-missing" } ],
                    "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("UPDATE_PIPELINES failed: Validation failed", result.error());
    verify(restClient, never()).deployFlow(any());
    verify(restClient, never()).deleteFlowByName(any());
  }

  @Test
  void updateRemovesTheFlowOfADeletedPipelineAndStopsMonitoringIt() throws Exception {
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "u", "adapter": "nifi",
              "operation": "UPDATE_PIPELINES",
              "artifacts": {},
              "dataPipelines": [
                { "id": "p-0", "version": "1", "action": "DELETE", "data": null,
                  "dataSourceIds": [], "dataSinkIds": [] } ] }
            """);

    try (MockedConstruction<NifiRuntimeMonitor> monitors =
        mockConstruction(NifiRuntimeMonitor.class)) {
      NifiSagaHandler monitored = new NifiSagaHandler();
      monitored.setTestNifiClient(restClient);
      monitored.initialize(config);

      SagaCommandResult result = monitored.handle(SagaCommandMessage.fromMap(payload));

      assertEquals("STEP_COMPLETED", result.type());
      assertEquals(List.of("p-0"), result.resultData().get("pipelineIds"));
      verify(restClient).deleteFlowByName("pipeline-p-0");
      verify(monitors.constructed().getFirst()).unregister("p-0");
    }
  }

  private Map<String, Object> restorePayload() throws Exception {
    Map<String, Object> payload = deployPayload();
    payload.put("type", "COMPENSATE_STEP");
    payload.put("operation", "RESTORE_PIPELINES");
    return payload;
  }

  @Test
  void restoreRedeploysFromArtifacts() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-1");

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(restorePayload()));

    assertEquals("COMPENSATION_COMPLETED", result.type());
    verify(restClient, times(1)).deployFlow(any(DeploymentPlan.class));
  }

  @Test
  void restoreWithoutArtifactsFailsTheCompensation() throws Exception {
    Map<String, Object> payload = restorePayload();
    payload.remove("artifacts");

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("COMPENSATION_FAILED", result.type());
    assertEquals("RESTORE_PIPELINES failed: Validation failed", result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void restoreWithMissingReferenceInALaterPipelineDeploysNothing() throws Exception {
    // A restore that deploys the pipelines before the defective one would leave a partial
    // compensation behind.
    Map<String, Object> payload = restorePayload();
    Map<String, Object> second =
        map(
            """
            { "id": "p-2", "version": "1", "action": "ADD",
              "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-missing"],
              "data": { "nodes": [
                  { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                  { "id": "sink", "kind": "sink", "sinkRef": "sk-missing" } ],
                "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } }
            """);
    @SuppressWarnings("unchecked")
    List<Object> pipelines = (List<Object>) payload.get("dataPipelines");
    pipelines.add(second);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("COMPENSATION_FAILED", result.type());
    assertEquals("RESTORE_PIPELINES failed: Validation failed", result.error());
    verify(restClient, never()).deployFlow(any());
  }

  @Test
  void deployResolvesMappingFromArtifacts() throws Exception {
    Map<String, Object> payload = deployPayload();
    artifacts(payload).put("map-1", map("{ \"fields\": { \"$.renamed\": \"$.id\" } }"));

    String snapshot = capturedSnapshot(SagaCommandMessage.fromMap(payload));

    assertTrue(snapshot.contains("/renamed"), "the mapping's target path is compiled");
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
  void deployDerivesUpsertKeysFromTheDataStructureTheSinkElementNames() throws Exception {
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
    // a present-but-non-object 'data' is corrupt — coercing it to an empty graph would misreport
    // the failure as a missing datasource node instead of the real payload corruption.
    SagaCommandResult result =
        handler.handle(
            SagaCommandMessage.fromMap(
                map(
                    """
                    { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
                      "operation": "DEPLOY_PIPELINES", "artifacts": {},
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
  void compensationTearsDownEveryDeployedPipeline() throws Exception {
    // The saga replays the deploy step's compensationData (its pipelineIds) as a COMPENSATE_STEP;
    // the sibling pipelines a failed deploy already deployed must all be removed (issue #1842).
    Map<String, Object> payload =
        map(
            """
            { "type": "COMPENSATE_STEP", "sagaId": "s", "stepId": "deploy-pipelines",
              "adapter": "nifi", "operation": "DELETE_PIPELINES",
              "pipelineIds": ["p-1", "p-2", "p-3"] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("COMPENSATION_COMPLETED", result.type());
    verify(restClient).deleteFlowByName(eq("pipeline-p-1"));
    verify(restClient).deleteFlowByName(eq("pipeline-p-2"));
    verify(restClient).deleteFlowByName(eq("pipeline-p-3"));
  }

  /**
   * A FROST-sink command: the shared payload of the FROST deploy tests. {@code projectIdField} is a
   * raw JSON member line (e.g. {@code "projectId": "5",}) or empty for an absent id.
   */
  private SagaCommandMessage frostCommand(String operation, String action, String projectIdField)
      throws Exception {
    return SagaCommandMessage.fromMap(
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "saga-frost", "stepId": "pipelines",
              "adapter": "nifi", "operation": "%s",
              %s
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-f": { "connectionType": "frost", "port": "ThingTree" } },
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "%s",
                "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-f"],
                "data": { "nodes": [
                    { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                    { "id": "sink", "kind": "sink", "sinkRef": "sk-f" } ],
                  "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } } ] }
            """
                .formatted(operation, projectIdField, action)));
  }

  @Test
  void deployResolvesFrostSinkNodeAndScopesFlowToProject() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-frost");

    SagaCommandResult result =
        handler.handle(frostCommand("DEPLOY_PIPELINES", "ADD", "\"projectId\": \"5\","));

    // the graph's frost sink node resolves from the artifacts; the passthrough flow
    // consumes the raw STA envelope (no mapping node)
    assertEquals("STEP_COMPLETED", result.type());
    ArgumentCaptor<DeploymentPlan> plan = ArgumentCaptor.forClass(DeploymentPlan.class);
    verify(restClient).deployFlow(plan.capture());
    // the saga's projectId scopes the flow to the dataset's FROST project
    assertTrue(plan.getValue().snapshotJson().contains("\"FROST Project Id\":\"5\""));
  }

  @Test
  void updateThreadsProjectIdIntoRedeployedFrostFlow() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-frost-upd");

    SagaCommandResult result =
        handler.handle(frostCommand("UPDATE_PIPELINES", "UPDATE", "\"projectId\": \"9\","));

    assertEquals("STEP_COMPLETED", result.type());
    ArgumentCaptor<DeploymentPlan> plan = ArgumentCaptor.forClass(DeploymentPlan.class);
    verify(restClient).deployFlow(plan.capture());
    assertTrue(plan.getValue().snapshotJson().contains("\"FROST Project Id\":\"9\""));
  }

  @Test
  void frostDeployWithNonNumericProjectIdFailsTheStep() throws Exception {
    // A present-but-non-numeric id must fail the step through the safe-external-message path
    // (never deploy, never leak the raw value across the result topic).
    SagaCommandResult result =
        handler.handle(frostCommand("DEPLOY_PIPELINES", "ADD", "\"projectId\": \"1) or true\","));

    assertEquals("STEP_FAILED", result.type());
    assertFalse(result.error().contains("1) or true"), "raw payload value must not leak");
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void frostDeployWithoutProjectIdFailsTheStep() throws Exception {
    // Without the FROST create-project step's result the flow would post to the server root,
    // invisible through the dataset's named API — fail the saga instead of deploying it.
    SagaCommandResult result = handler.handle(frostCommand("DEPLOY_PIPELINES", "ADD", ""));

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
  void blankPipelineIdIsRejected() throws Exception {
    // a blank id would build a "pipeline-" process-group name that could collide/mis-target —
    // reject
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES", "artifacts": {},
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
  void unreferencedArtifactIsInert() throws Exception {
    when(restClient.deployFlow(any())).thenReturn("pg-1");
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "obs" },
                "sk-unused": { "connectionType": "postgis", "tableName": "other" } },
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-1"],
                "data": { "nodes": [
                    { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                    { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                  "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_COMPLETED", result.type());
    verify(restClient, times(1)).deployFlow(any());
  }

  @Test
  void pipelinesResolveTheirOwnSources() throws Exception {
    // Two pipelines with different datasources deploy independently, each from its own sourceRef.
    when(restClient.deployFlow(any())).thenReturn("pg-1", "pg-2");
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["a/+"] },
                "ds-2": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["b/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "t1" },
                "sk-2": { "connectionType": "postgis", "tableName": "t2" } },
              "dataPipelines": [
                { "id": "p-1", "version": "1", "action": "ADD",
                  "dataSourceIds": ["ds-1"], "dataSinkIds": ["sk-1"],
                  "data": { "nodes": [
                      { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                      { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                    "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } },
                { "id": "p-2", "version": "1", "action": "ADD",
                  "dataSourceIds": ["ds-2"], "dataSinkIds": ["sk-2"],
                  "data": { "nodes": [
                      { "id": "src", "kind": "source", "sourceRef": "ds-2" },
                      { "id": "sink", "kind": "sink", "sinkRef": "sk-2" } ],
                    "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } }
              ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_COMPLETED", result.type());
    assertEquals(List.of("p-1", "p-2"), result.resultData().get("pipelineIds"));
    verify(restClient, times(2)).deployFlow(any());
  }

  @Test
  void pipelineWithoutSinkNodeFailsTheStep() throws Exception {
    // The graph is authoritative: a pipeline without a wired sink node cannot deploy — there is
    // no implicit platform-FROST default.
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES", "projectId": "5",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] } },
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "dataSourceIds": ["ds-1"], "dataSinkIds": [],
                "data": { "nodes": [
                    { "id": "src", "kind": "source", "sourceRef": "ds-1" } ],
                  "edges": [] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void graphEntityIdMissingFromPipelineAssociationFailsTheStep() throws Exception {
    // The graph's source node must reference an entity the pipeline actually declares — a
    // divergence between graph and dataSourceIds is a corrupt payload, not something to heal.
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "obs" } },
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "dataSourceIds": ["ds-other"], "dataSinkIds": ["sk-1"],
                "data": { "nodes": [
                    { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                    { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                  "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void nonListDataSourceIdsFailsTheStep() throws Exception {
    // A present-but-non-list dataSourceIds is a corrupt payload; coercing it to empty would fail
    // later with a misleading id-association error instead of naming the field defect.
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "obs" } },
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "dataSourceIds": "ds-1", "dataSinkIds": ["sk-1"],
                "data": { "nodes": [
                    { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                    { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                  "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void triggerWithoutPipelineAssociationsFailsTheStep() throws Exception {
    // A trigger that carries no dataSourceIds at all (it predates per-pipeline associations, or
    // the datasource was never associated) must fail the deploy; the internal detail names that
    // condition instead of misreporting an id mismatch.
    Map<String, Object> payload =
        map(
            """
            { "type": "EXECUTE_STEP", "sagaId": "s", "stepId": "d", "adapter": "nifi",
              "operation": "DEPLOY_PIPELINES",
              "artifacts": {
                "ds-1": { "connectionType": "mqtt", "urls": ["tcp://m:1883"], "topics": ["t/+"] },
                "sk-1": { "connectionType": "postgis", "tableName": "obs" } },
              "dataPipelines": [ { "id": "p-1", "version": "1", "action": "ADD",
                "data": { "nodes": [
                    { "id": "src", "kind": "source", "sourceRef": "ds-1" },
                    { "id": "sink", "kind": "sink", "sinkRef": "sk-1" } ],
                  "edges": [ { "id": "e1", "source": "src", "target": "sink" } ] } } ] }
            """);

    SagaCommandResult result = handler.handle(SagaCommandMessage.fromMap(payload));

    assertEquals("STEP_FAILED", result.type());
    verify(restClient, times(0)).deployFlow(any());
  }

  @Test
  void adapterNameIsNifi() {
    assertEquals("nifi", handler.adapter());
  }
}
