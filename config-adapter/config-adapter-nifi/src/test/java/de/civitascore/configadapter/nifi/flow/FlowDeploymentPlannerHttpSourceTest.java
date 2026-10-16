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

import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithHttpSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.httpSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.planner;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.req;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.stretchedKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowDeploymentPlannerHttpSourceTest {

  private static final String CRON = "0 */10 * * * ?";
  private static final String SECRET = "sup3r-s3cret-api-pw";

  private final ObjectMapper mapper = new ObjectMapper();

  private DeploymentPlan plan(String apiCallName, String authenticationJson) throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      return planner(resolver)
          .plan(
              req(
                  "p-http",
                  graphWithHttpSource(apiCallName, CRON),
                  httpSource(authenticationJson),
                  postgisSink()));
    }
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

  @Test
  void basicAuthPasswordReachesOnlyTheSensitiveMap() throws Exception {
    byte[] key = stretchedKey();
    String encrypted =
        "ENC("
            + CredentialEncryptor.encrypt(
                SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";

    DeploymentPlan plan =
        plan(
            "stations",
            "{ \"type\": \"BASIC\", \"username\": \"reader\", \"password\": \"%s\" }"
                .formatted(encrypted));

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
    DeploymentPlan plan = plan("stations", "{ \"type\": \"NONE\" }");

    assertEquals(
        "NONE", httpProcessor(plan).path("properties").path("Authentication Type").asText());
    assertFalse(plan.sensitivePropsByComponent().containsKey("HttpApiCall"));
  }
}
