/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import static org.junit.jupiter.api.Assertions.*;

import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Application class covering various configuration scenarios. Tests both
 * successful initialization and expected failure cases.
 *
 * <p>These tests target consumer / event-handler wiring, not saga bootstrap. The successful cases
 * inject {@link #NO_SAGA_BOOTSTRAP} so they don't each spin up a real Flowable engine on H2 and
 * deploy the coded saga processes (~5s per case). The real saga bootstrap is still exercised once
 * here by {@link #testDefaultConfigurationFile()} (which uses the production no-arg constructor)
 * and end-to-end by {@code EndToEndIntegrationTest}. The failure cases use the production
 * constructor on purpose: they throw at the consumer-factory stage, before any saga bootstrap, so
 * they stay fast.
 */
class ApplicationTest {

  /** A saga factory that builds no orchestrator, so a test skips the costly Flowable bootstrap. */
  private static final SagaComponentFactory NO_SAGA_BOOTSTRAP =
      new SagaComponentFactory() {
        @Override
        SagaComponents create(AppConfig config) {
          return new SagaComponents(Optional.empty());
        }
      };

  private static Application applicationWithoutSagaBootstrap(String configFileName)
      throws FatalAdapterException {
    return new Application(configFileName, NO_SAGA_BOOTSTRAP);
  }

  @Test
  @DisplayName("Should successfully create application with valid single adapter configuration")
  void testValidSingleAdapterConfiguration() {
    assertDoesNotThrow(
        () -> applicationWithoutSagaBootstrap("application-valid-single-adapter.properties"),
        "Application should initialize successfully with valid single adapter configuration");
  }

  @Test
  @DisplayName("Should successfully create application with valid multiple adapters configuration")
  void testValidMultipleAdaptersConfiguration() {
    assertDoesNotThrow(
        () -> applicationWithoutSagaBootstrap("application.properties"),
        "Application should initialize successfully with valid multiple adapters configuration");
  }

  @Test
  @DisplayName("Should fail when adapter 'okta' does not exist")
  void testFailureWithNonExistentOktaAdapter() {
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> new Application("application_wrong_adapter.properties"),
            "Application should throw FatalAdapterException when adapter 'okta' does not exist");

    String message = exception.getMessage();
    assertTrue(
        message.contains("Adapter 'okta' not found via ServiceLoader"),
        "Exception message should mention failure not found adapter 'okta', but was: " + message);
  }

  @Test
  @DisplayName("Should fail when no adapters are configured")
  void testFailureWithNoAdapters() {
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> new Application("application-no-adapters.properties"),
            "Application should throw FatalAdapterException when no adapters are configured");

    String message = exception.getMessage();
    assertTrue(
        message.contains("No adapters configured"),
        "Exception message should mention no adapters configured, but was: " + message);
  }

  @Test
  @DisplayName("Should fail when no event handler is configured")
  void testFailureWithNoEventHandler() {
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> new Application("application-no-eventhandler.properties"),
            "Application should throw FatalAdapterException when no event handler is configured");

    String message = exception.getMessage();
    assertTrue(
        message.contains("No event consumer configured"),
        "Exception message should mention no event consumer configured, but was: " + message);
  }

  @Test
  @DisplayName("Should fail with invalid configuration file")
  void testFailureWithInvalidConfigurationFile() {
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> new Application("non-existent-config.properties"),
            "Application should throw RuntimeException when configuration file does not exist");

    String message = exception.getMessage();
    assertTrue(
        message.contains("Unable to find non-existent-config.properties"),
        "Exception message should mention the missing config file, but was: " + message);
  }

  @Test
  @DisplayName("Should use default configuration file when no file specified")
  void testDefaultConfigurationFile() {
    assertDoesNotThrow(() -> new Application());
  }

  @Test
  @DisplayName("Should handle null configuration file name")
  void testNullConfigurationFileName() {
    assertThrows(
        NullPointerException.class,
        () -> new Application(null),
        "Application should throw NullPointerException when configuration file name is null");
  }

  @Test
  @DisplayName("Should successfully create application with separate consumer and publisher")
  void testSeparateConsumerAndPublisherConfiguration() {
    assertDoesNotThrow(
        () -> applicationWithoutSagaBootstrap("application-separate-consumer-publisher.properties"),
        "Application should initialize successfully with separate consumer and publisher configuration");
  }

  @Test
  @DisplayName("Should fail when both eventhandler.name and eventconsumer.name are specified")
  void testFailureWithConflictingHandlerConfiguration() {
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> new Application("application-conflicting-handler-config.properties"),
            "Application should throw FatalAdapterException when both eventhandler.name and eventconsumer.name are specified");

    String message = exception.getMessage();
    assertTrue(
        message.contains("Cannot specify both"),
        "Exception message should mention configuration conflict, but was: " + message);
  }

  @Test
  @DisplayName("Should fail when event consumer name does not exist")
  void testFailureWithNonExistentEventConsumer() {
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> new Application("application-nonexistent-consumer.properties"),
            "Application should throw FatalAdapterException when event consumer 'rabbitmq' does not exist");

    String message = exception.getMessage();
    assertTrue(
        message.contains("Event consumer 'rabbitmq' not found"),
        "Exception message should mention event consumer not found, but was: " + message);
  }

  @Test
  @DisplayName(
      "Should succeed with warning when event publisher name does not exist (publisher is optional)")
  void testSuccessWithNonExistentEventPublisher() {
    assertDoesNotThrow(
        () -> applicationWithoutSagaBootstrap("application-nonexistent-publisher.properties"),
        "Application should initialize successfully even when publisher is not found (publisher is optional)");
  }

  @Test
  @DisplayName("Should successfully create application with consumer only (no publisher)")
  void testConsumerOnlyConfiguration() {
    assertDoesNotThrow(
        () -> applicationWithoutSagaBootstrap("application-consumer-only.properties"),
        "Application should initialize successfully with consumer-only configuration");
  }
}
