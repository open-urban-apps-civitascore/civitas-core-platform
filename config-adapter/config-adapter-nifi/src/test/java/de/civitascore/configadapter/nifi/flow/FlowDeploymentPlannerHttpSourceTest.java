/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.HTTP_ELEMENT;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.MAPPINGS;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithHttpSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.httpSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.planner;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.req;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.stretchedKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowDeploymentPlannerHttpSourceTest {

  private static final String CRON = "0 */10 * * * ?";
  private static final String SECRET = "sup3r-s3cret-api-pw";

  private final ObjectMapper mapper = new ObjectMapper();

  private DeploymentPlan plan(String apiCallName, String authenticationJson) throws Exception {
    return plan(apiCallName, httpSource(authenticationJson), MAPPINGS);
  }

  private DeploymentPlan plan(
      String apiCallName, Datasource source, Map<String, Map<String, Object>> artifacts)
      throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      return planner(resolver)
          .plan(
              new PipelineDeploymentRequest(
                  "p-http",
                  graphWithHttpSource(apiCallName, CRON),
                  source,
                  postgisSink(),
                  artifacts));
    }
  }

  private FatalAdapterException refusal(
      Datasource source, Map<String, Map<String, Object>> artifacts) {
    return assertThrows(FatalAdapterException.class, () -> plan("stations", source, artifacts));
  }

  private static Map<String, Map<String, Object>> artifactsWithDataStructure(
      Map<String, Object> dataStructure) {
    Map<String, Map<String, Object>> artifacts = new LinkedHashMap<>(MAPPINGS);
    artifacts.put(HTTP_ELEMENT, dataStructure);
    return artifacts;
  }

  private JsonNode processorNamed(JsonNode flow, String name) {
    for (JsonNode processor : flow.path("flowContents").path("processors")) {
      if (name.equals(processor.path("name").asText())) {
        return processor;
      }
    }
    throw new AssertionError("no processor named " + name);
  }

  private List<JsonNode> connectionsFrom(JsonNode flow, String sourceId, String relationship) {
    List<JsonNode> found = new ArrayList<>();
    for (JsonNode connection : flow.path("flowContents").path("connections")) {
      if (sourceId.equals(connection.path("source").path("id").asText())
          && connection.path("selectedRelationships").toString().contains(relationship)) {
        found.add(connection);
      }
    }
    return found;
  }

  private List<JsonNode> processorsOfType(JsonNode flow, String typeSuffix) {
    List<JsonNode> found = new ArrayList<>();
    for (JsonNode processor : flow.path("flowContents").path("processors")) {
      if (processor.path("type").asText().endsWith(typeSuffix)) {
        found.add(processor);
      }
    }
    return found;
  }

  private JsonNode httpProcessor(DeploymentPlan plan) throws Exception {
    List<JsonNode> found = processorsOfType(mapper.readTree(plan.snapshotJson()), "GetHttpApiCall");
    assertEquals(1, found.size());
    return found.getFirst();
  }

  @Test
  void flowCallsTheApiCallThatTheSourceNodeNames() throws Exception {
    JsonNode properties = httpProcessor(plan("stations", null)).path("properties");

    assertEquals("https://api.example.org/v1", properties.path("Base URL").asText());
    assertEquals("NONE", properties.path("Authentication Type").asText());
    assertEquals(
        mapper.readTree(
            """
            { "name": "stations", "method": "GET", "path": "/stations",
              "queryParameters": [ { "name": "limit", "value": "100" } ],
              "headers": [ { "name": "Accept", "value": "application/json" } ] }
            """),
        mapper.readTree(properties.path("API Call").asText()));
  }

  @Test
  void recordSchemaIsTheDataStructureThatTheDataSourceNames() throws Exception {
    JsonNode properties = httpProcessor(plan("stations", null)).path("properties");

    assertEquals(
        mapper.valueToTree(MAPPINGS.get(HTTP_ELEMENT)),
        mapper.readTree(properties.path("Record Schema").asText()));
  }

  @Test
  void dataSourceWithoutDataStructureIsRefusedAsInvalidPayload() throws Exception {
    Datasource source = httpSource(null);
    source.handleUnknownProperty("element", null);

    FatalAdapterException refusal = refusal(source, MAPPINGS);

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, refusal.getErrorCode());
    assertTrue(refusal.getInternalMessage().contains("'h1'"), refusal.getInternalMessage());
  }

  @Test
  void dataStructureMissingFromTheArtifactsIsRefusedAsInvalidPayload() throws Exception {
    Map<String, Map<String, Object>> artifacts = new LinkedHashMap<>(MAPPINGS);
    artifacts.remove(HTTP_ELEMENT);

    FatalAdapterException refusal = refusal(httpSource(null), artifacts);

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, refusal.getErrorCode());
    assertTrue(refusal.getInternalMessage().contains("'h1'"), refusal.getInternalMessage());
    assertTrue(refusal.getInternalMessage().contains(HTTP_ELEMENT), refusal.getInternalMessage());
  }

  @Test
  void parameterReferenceInTheDataStructureIsRefusedByTheLiteralCheck() throws Exception {
    Map<String, Map<String, Object>> artifacts =
        artifactsWithDataStructure(
            Map.of("type", "object", "description", "reads #{secret} on import"));

    FatalAdapterException refusal = refusal(httpSource(null), artifacts);

    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, refusal.getErrorCode());
    assertTrue(
        refusal.getInternalMessage().contains("Record Schema"), refusal.getInternalMessage());
  }

  @Test
  void stopOfTheProcessorEndsInTheStopSinkThroughAStopStamp() throws Exception {
    JsonNode flow = mapper.readTree(plan("stations", null).snapshotJson());
    String processorId = httpProcessor(plan("stations", null)).path("identifier").asText();
    JsonNode stopSink = processorNamed(flow, NifiRestClient.STOP_SINK);

    List<JsonNode> toStamp = connectionsFrom(flow, processorId, "\"stop\"");
    assertEquals(1, toStamp.size());
    String stampId = toStamp.getFirst().path("destination").path("id").asText();
    JsonNode stamp = null;
    for (JsonNode processor : processorsOfType(flow, "UpdateAttribute")) {
      if (stampId.equals(processor.path("identifier").asText())) {
        stamp = processor;
      }
    }
    assertNotNull(stamp, "the stop relationship ends in an UpdateAttribute");
    assertEquals(
        "${http.error.message}", stamp.path("properties").path("core.stop.cause").asText());
    List<JsonNode> toSink = connectionsFrom(flow, stampId, "\"success\"");
    assertEquals(1, toSink.size());
    assertEquals(
        stopSink.path("identifier").asText(),
        toSink.getFirst().path("destination").path("id").asText());
    assertFalse(
        httpProcessor(plan("stations", null))
            .path("autoTerminatedRelationships")
            .toString()
            .contains("stop"));
  }

  @Test
  void stopSinkLogsTheCauseAtErrorWithoutAPrefix() throws Exception {
    JsonNode flow = mapper.readTree(plan("stations", null).snapshotJson());

    JsonNode properties = processorNamed(flow, NifiRestClient.STOP_SINK).path("properties");

    assertEquals("error", properties.path("log-level").asText());
    assertEquals("${core.stop.cause}", properties.path("log-message").asText());
    assertTrue(properties.path("log-prefix").isNull());
    assertEquals(1, processorsOfType(flow, "UpdateAttribute").size());
  }

  @Test
  void flowCallsTheSecondApiCallWithoutItsPagination() throws Exception {
    JsonNode properties = httpProcessor(plan("readings", null)).path("properties");

    assertEquals(
        mapper.readTree(
            """
            { "name": "readings", "method": "GET", "path": "/readings",
              "queryParameters": [], "headers": [] }
            """),
        mapper.readTree(properties.path("API Call").asText()));
  }

  @Test
  void cronNodeSchedulesOneRunAtATimeOnThePrimaryNode() throws Exception {
    JsonNode processor = httpProcessor(plan("stations", null));

    assertEquals("CRON_DRIVEN", processor.path("schedulingStrategy").asText());
    assertEquals(CRON, processor.path("schedulingPeriod").asText());
    assertEquals("PRIMARY", processor.path("executionNode").asText());
    assertEquals(1, processor.path("concurrentlySchedulableTaskCount").asInt());
  }

  @Test
  void webClientOfTheProcessorDoesNotFollowRedirects() throws Exception {
    DeploymentPlan plan = plan("stations", null);
    String serviceId =
        httpProcessor(plan).path("properties").path("Web Client Service Provider").asText();

    JsonNode service = null;
    for (JsonNode candidate :
        mapper.readTree(plan.snapshotJson()).path("flowContents").path("controllerServices")) {
      if (serviceId.equals(candidate.path("identifier").asText())) {
        service = candidate;
      }
    }
    assertNotNull(service, "the processor refers to a controller service of the flow");
    assertEquals("IGNORED", service.path("properties").path("Redirect Handling Strategy").asText());
  }

  @Test
  void failureOfTheProcessorEndsInTheErrorSink() throws Exception {
    DeploymentPlan plan = plan("stations", null);
    JsonNode flow = mapper.readTree(plan.snapshotJson());
    String processorId = httpProcessor(plan).path("identifier").asText();
    String errorSinkId =
        processorsOfType(flow, "LogMessage").getFirst().path("identifier").asText();

    List<String> failureTargets = new ArrayList<>();
    for (JsonNode connection : flow.path("flowContents").path("connections")) {
      if (processorId.equals(connection.path("source").path("id").asText())
          && connection.path("selectedRelationships").toString().contains("\"failure\"")) {
        failureTargets.add(connection.path("destination").path("id").asText());
      }
    }
    assertEquals(List.of(errorSinkId), failureTargets);
  }

  private static String encryptedSecret() throws Exception {
    return "ENC("
        + CredentialEncryptor.encrypt(
            SECRET, stretchedKey(), CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
        + ")";
  }

  @Test
  void basicAuthPasswordReachesOnlyTheSensitiveMap() throws Exception {
    DeploymentPlan plan =
        plan(
            "stations",
            "{ \"type\": \"BASIC\", \"username\": \"reader\", \"password\": \"%s\" }"
                .formatted(encryptedSecret()));

    JsonNode properties = httpProcessor(plan).path("properties");
    assertEquals("BASIC", properties.path("Authentication Type").asText());
    assertEquals("reader", properties.path("Basic Auth Username").asText());
    assertEquals(
        Map.of("Basic Auth Password", SECRET), plan.sensitivePropsByComponent().get("HttpApiCall"));
    assertFalse(plan.snapshotJson().contains(SECRET));
  }

  @Test
  void blankApiCallNameOnAnMqttSourceNodeNamesNoApiCall() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  req(
                      "p-mqtt",
                      graphWithHttpSource("", null),
                      NifiTestFixtures.mqttSource(null),
                      postgisSink()));

      assertEquals(1, processorsOfType(mapper.readTree(plan.snapshotJson()), "ConsumeMQTT").size());
    }
  }

  @Test
  void withoutAuthenticationNothingIsPushedToTheProcessor() throws Exception {
    DeploymentPlan plan =
        plan(
            "stations",
            "{ \"type\": \"NONE\", \"password\": \"%s\" }".formatted(encryptedSecret()));

    assertEquals(
        "NONE", httpProcessor(plan).path("properties").path("Authentication Type").asText());
    assertFalse(plan.sensitivePropsByComponent().containsKey("HttpApiCall"));
  }
}
