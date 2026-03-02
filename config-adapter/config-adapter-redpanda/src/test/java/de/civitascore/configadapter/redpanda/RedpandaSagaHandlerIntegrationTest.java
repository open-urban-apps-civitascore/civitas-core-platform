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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration test for {@link RedpandaSagaHandler} against a real RedPanda Connect instance.
 * Requires Docker.
 *
 * <p>Tests are ordered via {@code @Order} because they form a CRUD lifecycle sequence (deploy →
 * update → delete → compensate) against a shared Testcontainer. This is a deliberate performance
 * trade-off: starting a fresh container per test adds ~10 s each. Guard methods like {@code
 * assertPipelineExists()} allow verifying preconditions when running tests individually.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedpandaSagaHandlerIntegrationTest extends AbstractRedpandaIntegrationTest {

  private static RedpandaSagaHandler handler;

  @BeforeAll
  static void setUpHandler() {
    handler = new RedpandaSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("redpanda.url", "http://localhost:4195")).thenReturn(baseUrl);
    handler.initialize(mockConfig);
  }

  @AfterAll
  static void tearDownHandler() {
    if (handler != null) {
      handler.close();
    }
  }

  @Test
  @Order(1)
  @DisplayName("DEPLOY_PIPELINES creates pipelines in RedPanda Connect")
  void handle_deployPipelines_pipelinesCreatedInRedpanda() {
    SagaCommandMessage command =
        createIntegrationCommand(
            "EXECUTE_STEP",
            "DEPLOY_PIPELINES",
            Map.of(
                "datasetId",
                "ds-it-1",
                "dataPipelines",
                List.of(
                    Map.of(
                        "id",
                        "it-pl-1",
                        "data",
                        Map.of("input", Map.of("generate", Map.of("mapping", "root = \"hello\"")))),
                    Map.of(
                        "id",
                        "it-pl-2",
                        "data",
                        Map.of(
                            "input", Map.of("generate", Map.of("mapping", "root = \"world\"")))))));

    SagaCommandResult result = handler.handle(command);

    assertEquals("STEP_COMPLETED", result.type());
    assertNull(result.error());

    @SuppressWarnings("unchecked")
    List<String> pipelineIds = (List<String>) result.resultData().get("pipelineIds");
    assertNotNull(pipelineIds);
    assertEquals(2, pipelineIds.size());

    // Verify pipelines exist via direct HTTP check
    assertPipelineExists("it-pl-1");
    assertPipelineExists("it-pl-2");
  }

  @Test
  @Order(2)
  @DisplayName("UPDATE_PIPELINES modifies existing pipelines")
  void handle_updatePipelines_pipelinesUpdatedInRedpanda() {
    SagaCommandMessage command =
        createIntegrationCommand(
            "EXECUTE_STEP",
            "UPDATE_PIPELINES",
            Map.of(
                "datasetId",
                "ds-it-1",
                "dataPipelines",
                List.of(
                    Map.of(
                        "id", "it-pl-1",
                        "action", "UPDATE",
                        "data",
                            Map.of(
                                "input",
                                Map.of("generate", Map.of("mapping", "root = \"updated\"")))))));

    SagaCommandResult result = handler.handle(command);

    assertEquals("STEP_COMPLETED", result.type());
    assertNull(result.error());

    // Verify pipeline still exists after update
    assertPipelineExists("it-pl-1");
  }

  @Test
  @Order(3)
  @DisplayName("DELETE_PIPELINES removes pipelines from RedPanda Connect")
  void handle_deletePipelines_pipelinesRemovedFromRedpanda() {
    SagaCommandMessage command =
        createIntegrationCommand(
            "EXECUTE_STEP",
            "DELETE_PIPELINES",
            Map.of("datasetId", "ds-it-1", "pipelineIds", List.of("it-pl-1", "it-pl-2")));

    SagaCommandResult result = handler.handle(command);

    assertEquals("STEP_COMPLETED", result.type());
    assertNull(result.error());

    // Verify pipelines no longer exist
    assertPipelineNotExists("it-pl-1");
    assertPipelineNotExists("it-pl-2");
  }

  @Test
  @Order(4)
  @DisplayName("DEPLOY then DELETE compensation flow")
  void handle_deployThenCompensate_pipelinesRemovedAfterCompensation() {
    // Deploy
    SagaCommandMessage deployCommand =
        createIntegrationCommand(
            "EXECUTE_STEP",
            "DEPLOY_PIPELINES",
            Map.of(
                "datasetId",
                "ds-it-2",
                "dataPipelines",
                List.of(
                    Map.of(
                        "id",
                        "it-comp-pl",
                        "data",
                        Map.of(
                            "input", Map.of("generate", Map.of("mapping", "root = \"test\"")))))));

    SagaCommandResult deployResult = handler.handle(deployCommand);
    assertEquals("STEP_COMPLETED", deployResult.type());
    assertPipelineExists("it-comp-pl");

    // Compensate (DELETE)
    SagaCommandMessage compensateCommand =
        createIntegrationCommand(
            "COMPENSATE_STEP",
            "DELETE_PIPELINES",
            Map.of("datasetId", "ds-it-2", "pipelineIds", List.of("it-comp-pl")));

    SagaCommandResult compResult = handler.handle(compensateCommand);
    assertEquals("COMPENSATION_COMPLETED", compResult.type());
    assertPipelineNotExists("it-comp-pl");
  }

  @Test
  @Order(5)
  @DisplayName("DELETE non-existent pipeline returns STEP_COMPLETED (idempotent)")
  void handle_deleteNonExistent_returnsStepCompleted() {
    SagaCommandMessage command =
        createIntegrationCommand(
            "EXECUTE_STEP",
            "DELETE_PIPELINES",
            Map.of("datasetId", "ds-it-3", "pipelineIds", List.of("non-existent-pipeline")));

    SagaCommandResult result = handler.handle(command);

    assertEquals("STEP_COMPLETED", result.type());
    assertNull(result.error());
  }

  @Test
  @Order(6)
  @DisplayName("DEPLOY with ${FROST_BASE} placeholder resolves targetUrl")
  void handle_deployWithFrostBase_resolvesPlaceholder() {
    SagaCommandMessage command =
        createIntegrationCommand(
            "EXECUTE_STEP",
            "DEPLOY_PIPELINES",
            Map.of(
                "datasetId",
                "ds-it-frost",
                "targetUrl",
                "https://frost.example.com/FROST-Server/v1.1",
                "dataPipelines",
                List.of(
                    Map.of(
                        "id",
                        "it-frost-pl",
                        "data",
                        Map.of(
                            "input",
                            Map.of("generate", Map.of("mapping", "root = \"test\"")),
                            "output",
                            Map.of(
                                "http_client",
                                Map.of("url", "${FROST_BASE}/Things", "verb", "POST")))))));

    SagaCommandResult result = handler.handle(command);

    assertEquals("STEP_COMPLETED", result.type());
    assertNull(result.error());
    assertPipelineExists("it-frost-pl");

    // Cleanup
    handler.handle(
        createIntegrationCommand(
            "EXECUTE_STEP",
            "DELETE_PIPELINES",
            Map.of("datasetId", "ds-it-frost", "pipelineIds", List.of("it-frost-pl"))));
  }
}
