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

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for RedPanda Connect integration tests. Uses the singleton container pattern so the
 * container starts only once per JVM. Provides a shared HTTP client and pipeline assertion helpers.
 */
@SuppressWarnings("resource")
abstract class AbstractRedpandaIntegrationTest {

  protected static final int CONNECT_PORT = 4195;

  static final GenericContainer<?> redpandaConnect =
      new GenericContainer<>(DockerImageName.parse("redpandadata/connect"))
          .withCommand("streams")
          .withExposedPorts(CONNECT_PORT)
          .waitingFor(Wait.forHttp("/ready").forPort(CONNECT_PORT).forStatusCode(200));

  static {
    redpandaConnect.start();

    Runtime.getRuntime().addShutdownHook(new Thread(redpandaConnect::stop));
  }

  protected static String baseUrl;
  protected static Client httpClient;

  @BeforeAll
  static void setUpBaseContainer() {
    baseUrl =
        "http://" + redpandaConnect.getHost() + ":" + redpandaConnect.getMappedPort(CONNECT_PORT);
    httpClient =
        ClientBuilder.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
  }

  @AfterAll
  static void tearDownBaseContainer() {
    if (httpClient != null) {
      httpClient.close();
    }
  }

  protected Response getPipeline(String pipelineId) {
    return httpClient
        .target(baseUrl)
        .path("streams")
        .path(pipelineId)
        .request(MediaType.APPLICATION_JSON)
        .get();
  }

  protected void assertPipelineExists(String pipelineId) {
    try (Response response = getPipeline(pipelineId)) {
      assertEquals(
          200,
          response.getStatus(),
          "Pipeline " + pipelineId + " should exist, got HTTP " + response.getStatus());
    }
  }

  protected void assertPipelineNotExists(String pipelineId) {
    try (Response response = getPipeline(pipelineId)) {
      assertEquals(
          404,
          response.getStatus(),
          "Pipeline " + pipelineId + " should not exist, got HTTP " + response.getStatus());
    }
  }
}
