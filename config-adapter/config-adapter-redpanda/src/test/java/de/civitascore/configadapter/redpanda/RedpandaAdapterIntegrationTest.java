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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.redpanda.PipelineConfigValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for {@link RedpandaAdapter} against a real RedPanda Connect instance. Requires
 * Docker.
 *
 * <p>Tests are ordered via {@code @Order} because they form a CRUD lifecycle sequence (create →
 * update → delete) against a shared Testcontainer. This is a deliberate performance trade-off:
 * starting a fresh container per test adds ~10 s each. Guard methods like {@code
 * ensurePipelineExists()} ensure each test can also run in isolation.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedpandaAdapterIntegrationTest {

  private static final int CONNECT_PORT = 4195;
  private static final String PIPELINE_ID = "integration-test-pipeline";

  @Container
  static final GenericContainer<?> redpandaConnect =
      new GenericContainer<>(DockerImageName.parse("redpandadata/connect"))
          .withCommand("streams")
          .withExposedPorts(CONNECT_PORT)
          .waitingFor(Wait.forHttp("/ready").forPort(CONNECT_PORT).forStatusCode(200));

  private static String baseUrl;
  private static Client httpClient;
  private RedpandaAdapter adapter;

  @BeforeAll
  static void setUpContainer() {
    baseUrl =
        "http://" + redpandaConnect.getHost() + ":" + redpandaConnect.getMappedPort(CONNECT_PORT);
    httpClient =
        ClientBuilder.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
  }

  @AfterAll
  static void tearDown() {
    if (httpClient != null) {
      httpClient.close();
    }
  }

  @BeforeEach
  void setUp() {
    adapter = new RedpandaAdapter();
    AdapterConfig config = new TestAdapterConfig(baseUrl);
    adapter.initialize(config);
  }

  @Test
  @Order(1)
  @DisplayName("CREATE pipeline via adapter, then verify it exists via GET")
  void processConfigEvent_createOperation_pipelineExistsInRedpanda() {
    ConfigEvent event = createEvent(Operation.CREATE, PIPELINE_ID);

    assertDoesNotThrow(
        () -> adapter.processConfigEvent("de.civitascore.data.pipeline.created", event));

    try (Response response = getPipeline(PIPELINE_ID)) {
      assertEquals(200, response.getStatus());
    }
  }

  @Test
  @Order(2)
  @DisplayName("UPDATE existing pipeline, then verify via GET")
  void processConfigEvent_updateOperation_pipelineUpdatedInRedpanda() {
    // Ensure pipeline exists first
    ensurePipelineExists(PIPELINE_ID);

    ConfigEvent event = createEvent(Operation.UPDATE, PIPELINE_ID);

    assertDoesNotThrow(
        () -> adapter.processConfigEvent("de.civitascore.data.pipeline.updated", event));

    try (Response response = getPipeline(PIPELINE_ID)) {
      assertEquals(200, response.getStatus());
    }
  }

  @Test
  @Order(3)
  @DisplayName("DELETE existing pipeline, then verify 404")
  void processConfigEvent_deleteOperation_pipelineRemovedFromRedpanda() {
    // Ensure pipeline exists first
    ensurePipelineExists(PIPELINE_ID);

    ConfigEvent event = createEvent(Operation.DELETE, PIPELINE_ID);

    assertDoesNotThrow(
        () -> adapter.processConfigEvent("de.civitascore.data.pipeline.deleted", event));

    try (Response response = getPipeline(PIPELINE_ID)) {
      assertEquals(404, response.getStatus());
    }
  }

  @Test
  @Order(4)
  @DisplayName("DELETE non-existent pipeline throws FatalAdapterException")
  void processConfigEvent_deleteNonExistent_throwsFatalAdapterException() {
    ConfigEvent event = createEvent(Operation.DELETE, "non-existent-pipeline");

    assertThrows(
        FatalAdapterException.class,
        () -> adapter.processConfigEvent("de.civitascore.data.pipeline.deleted", event));
  }

  private ConfigEvent createEvent(Operation operation, String pipelineId) {
    PipelineConfigValue configValue = new PipelineConfigValue();
    configValue.setPipelineId(pipelineId);

    Config config = new Config("redpanda-pipeline", configValue);
    Payload payload = new Payload("redpanda-connect", "pipelines", operation, config);
    Metadata metadata =
        new Metadata(
            "msg-it-1",
            OffsetDateTime.now(),
            "integration-test",
            "corr-it-1",
            "1.0",
            "de.civitascore.data.pipeline.processing.result");
    return new ConfigEvent(metadata, payload);
  }

  private Response getPipeline(String pipelineId) {
    return httpClient
        .target(baseUrl)
        .path("streams")
        .path(pipelineId)
        .request(MediaType.APPLICATION_JSON)
        .get();
  }

  private void ensurePipelineExists(String pipelineId) {
    try (Response check = getPipeline(pipelineId)) {
      if (check.getStatus() == 404) {
        ConfigEvent event = createEvent(Operation.CREATE, pipelineId);
        try {
          adapter.processConfigEvent("de.civitascore.data.pipeline.created", event);
        } catch (Exception e) {
          throw new RuntimeException("Failed to create pipeline for test setup", e);
        }
      }
    }
  }

  /** Simple AdapterConfig for integration tests. */
  private static class TestAdapterConfig implements AdapterConfig {
    private final String baseUrl;

    TestAdapterConfig(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    @Override
    public String getProperty(String key) {
      return switch (key) {
        case "redpanda.url" -> baseUrl;
        case "redpanda.topics" ->
            "de.civitascore.data.pipeline.created,de.civitascore.data.pipeline.updated,de.civitascore.data.pipeline.deleted";
        default -> null;
      };
    }

    @Override
    public String getProperty(String key, String defaultValue) {
      String value = getProperty(key);
      return value != null ? value : defaultValue;
    }
  }
}
