/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AppConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration2.MapConfiguration;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link FrostSagaHandler} using a real FROST-Server in Docker.
 *
 * <p>These tests document a known FROST quirk: instead of returning HTTP 409 Conflict on a
 * duplicate project name, FROST returns HTTP 500 with body {@code {"message":"Failed to store
 * data."}}. The handler must treat this as an idempotent success.
 */
class FrostSagaHandlerIntegrationTest extends AbstractFrostIntegrationTest {

  private FrostSagaHandler handler;
  private Client httpClient;
  private String frostBaseUrl;

  @BeforeEach
  void setUp() {
    frostBaseUrl =
        "http://" + FROST.getHost() + ":" + FROST.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = ClientBuilder.newClient();

    waitForFrostReady(frostBaseUrl);

    Map<String, Object> props = new HashMap<>();
    props.put("frost.url", frostBaseUrl);
    props.put("frost.api.key", "test-api-key");
    props.put("frost.api.key.header", "X-API-Key");
    AppConfig config = new AppConfig(new MapConfiguration(props));

    handler = new FrostSagaHandler();
    handler.initialize(config);

    Client patchCapableClient =
        ClientBuilder.newBuilder()
            .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    handler.setTestClient(patchCapableClient);
  }

  @AfterEach
  void tearDown() {
    if (handler != null) {
      handler.close();
    }
    if (httpClient != null) {
      httpClient.close();
    }
  }

  /**
   * Documents that FROST returns HTTP 500 (not 409) when a project with the same name already
   * exists. This is a quirk of the FROST Projects plugin that the handler must work around.
   */
  @Test
  void frostReturnsHttp500WhenCreatingProjectWithDuplicateName() {
    String projectName = "Duplicate-" + UUID.randomUUID();
    Map<String, Object> projectBody = Map.of("name", projectName, "description", "");

    try (Response first =
        httpClient
            .target(frostBaseUrl)
            .path("Projects")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(projectBody))) {
      assertEquals(201, first.getStatus(), "First create should succeed");
    }

    try (Response second =
        httpClient
            .target(frostBaseUrl)
            .path("Projects")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(projectBody))) {
      assertEquals(
          500,
          second.getStatus(),
          "FROST returns 500 (not 409) for duplicate project name — if this fails, FROST behaviour"
              + " changed and the idempotence workaround in FrostSagaHandler can be simplified");
    }
  }

  /**
   * CREATE_PROJECT must be idempotent: calling it a second time with the same name must succeed and
   * return the same projectId as the first call.
   *
   * <p>This test currently fails because FrostSagaHandler propagates the HTTP 500 from FROST as a
   * STEP_FAILED result instead of recognising it as a duplicate and returning the existing project.
   */
  @Test
  void createProjectIsIdempotentWhenProjectAlreadyExists() {
    String datasetName = "Idempotent-" + UUID.randomUUID();
    SagaCommandMessage command =
        new SagaCommandMessage(
            "EXECUTE_STEP",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "create-frost-project",
            "frost",
            "CREATE_PROJECT",
            Map.of("datasetName", datasetName, "description", "idempotence test"));

    SagaCommandResult firstResult = handler.handle(command);
    assertEquals(
        "STEP_COMPLETED",
        firstResult.type(),
        () -> "First CREATE_PROJECT failed: " + firstResult.error());

    String firstProjectId = (String) firstResult.resultData().get("projectId");

    SagaCommandResult secondResult = handler.handle(command);
    assertEquals(
        "STEP_COMPLETED",
        secondResult.type(),
        () -> "Second CREATE_PROJECT (idempotent retry) failed: " + secondResult.error());
    assertEquals(
        firstProjectId,
        secondResult.resultData().get("projectId"),
        "Idempotent retry must return the same projectId");
  }

  private void waitForFrostReady(String baseUrl) {
    await()
        .atMost(60, SECONDS)
        .pollInterval(2, SECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (Response response =
                  httpClient.target(baseUrl).path("Projects").request().get()) {
                assertEquals(200, response.getStatus());
              }
            });
  }
}
